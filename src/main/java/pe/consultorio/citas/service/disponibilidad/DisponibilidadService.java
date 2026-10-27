package pe.consultorio.citas.service.disponibilidad;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.ExcepcionHorario;
import pe.consultorio.citas.entity.HorarioVersion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.repository.BloqueoTemporalRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.ExcepcionHorarioRepository;
import pe.consultorio.citas.repository.HorarioVersionRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.service.ConfiguracionService;

/**
Alimenta el motor de disponibilidad con los datos de un médico y un día: franjas de la versión de horario vigente
menos citas, excepciones, retenciones de otras cuentas y, si es hoy, el pasado.
*/
@Service
@Transactional(readOnly = true)
public class DisponibilidadService {

    /** Cuántos días hacia adelante se busca y cuántos días con horarios se devuelven. */
    private static final int DIAS_BUSQUEDA = 60;
    private static final int DIAS_A_MOSTRAR = 3;

    private final MedicoRepository medicos;
    private final HorarioVersionRepository versiones;
    private final ExcepcionHorarioRepository excepciones;
    private final CitaRepository citas;
    private final BloqueoTemporalRepository bloqueos;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public DisponibilidadService(MedicoRepository medicos, HorarioVersionRepository versiones,
            ExcepcionHorarioRepository excepciones, CitaRepository citas, BloqueoTemporalRepository bloqueos,
            ConfiguracionService configuracion, Clock reloj) {
        this.medicos = medicos;
        this.versiones = versiones;
        this.excepciones = excepciones;
        this.citas = citas;
        this.bloqueos = bloqueos;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /**
    Los tres primeros días, desde {@code desde}, que tengan al menos un horario libre para una cita de
    {@code duracionMin} minutos. No ofrece horarios retenidos por otra cuenta.
    */
    public List<OfertaDia> ofertasProximas(Long idMedico, int duracionMin, LocalDate desde, Long idCuentaQuePide) {
        List<OfertaDia> dias = new ArrayList<>();
        for (int i = 0; i < DIAS_BUSQUEDA && dias.size() < DIAS_A_MOSTRAR; i++) {
            LocalDate fecha = desde.plusDays(i);
            List<Oferta> ofertas = ofertasDelDia(idMedico, duracionMin, fecha, idCuentaQuePide);
            if (!ofertas.isEmpty()) {
                dias.add(new OfertaDia(fecha, ofertas));
            }
        }
        return dias;
    }

    /** Horarios libres de un día para una cita de {@code duracionMin} minutos, ya ordenados. */
    public List<Oferta> ofertasDelDia(Long idMedico, int duracionMin, LocalDate fecha, Long idCuentaQuePide) {
        RangeSet<LocalDateTime> libres = libres(idMedico, fecha, true, idCuentaQuePide);
        if (libres.isEmpty()) {
            return List.of();
        }
        return MotorDisponibilidad.ofertas(libres, duracionMin, configuracion.incremento(),
            configuracion.huecoMinimoUtil());
    }

    /** Bloques libres del día, sin descontar retenciones temporales ni el pasado. */
    public RangeSet<LocalDateTime> bloquesLibresDelDia(Long idMedico, LocalDate fecha) {
        return libres(idMedico, fecha, false, null);
    }

    private RangeSet<LocalDateTime> libres(Long idMedico, LocalDate fecha, boolean paraOfrecer, Long idCuentaQuePide) {
        Medico m = medicos.findById(idMedico)
            .orElseThrow(() -> new RecursoNoEncontradoException("Médico inexistente " + idMedico));
        if (!m.isActivo()) {
            return TreeRangeSet.create();
        }
        List<Range<LocalDateTime>> franjas = franjas(idMedico, fecha);
        if (franjas.isEmpty()) {
            return TreeRangeSet.create();
        }
        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();
        List<Range<LocalDateTime>> ocupados = new ArrayList<>();

        for (ExcepcionHorario e : excepciones.findByMedicoIdMedicoAndFechaFinGreaterThanEqualOrderByFechaInicio(
                idMedico, fecha)) {
            if (e.getFechaInicio().isAfter(fecha)) {
                continue;
            }
            ocupados.add(e.getHoraInicio() == null ? Range.closedOpen(inicioDia, finDia)
                : Range.closedOpen(fecha.atTime(e.getHoraInicio()), fecha.atTime(e.getHoraFin())));
        }
        citas.delMedicoQueCruzan(idMedico, inicioDia, finDia, CitaRepository.CANCELADAS)
            .forEach(c -> ocupados.add(Range.closedOpen(c.getInicio(), c.getFin())));

        if (paraOfrecer) {
            LocalDateTime ahora = LocalDateTime.now(reloj);
            bloqueos.vigentesQueCruzan(idMedico, inicioDia, finDia, ahora).stream()
                .filter(b -> idCuentaQuePide == null || !b.getCuenta().getIdCuenta().equals(idCuentaQuePide))
                .forEach(b -> ocupados.add(Range.closedOpen(b.getInicio(), b.getFin())));
            if (fecha.equals(ahora.toLocalDate())) {
                ocupados.add(Range.lessThan(Rejilla.redondearArriba(ahora, configuracion.incremento())));
            } else if (fecha.isBefore(ahora.toLocalDate())) {
                return TreeRangeSet.create();
            }
        }
        return MotorDisponibilidad.bloquesLibres(franjas, ocupados);
    }

    /** Franjas de la versión de horario vigente ese día, para el día de la semana de la fecha. */
    private List<Range<LocalDateTime>> franjas(Long idMedico, LocalDate fecha) {
        int dia = fecha.getDayOfWeek().getValue();
        return versiones.findByMedicoIdMedicoOrderByVigenteDesdeDesc(idMedico).stream()
            .filter(v -> vigente(v, fecha))
            .findFirst()
            .map(v -> v.getFranjas().stream()
                .filter(f -> f.getDiaSemana() == dia)
                .map(f -> Range.closedOpen(fecha.atTime(f.getHoraInicio()), fecha.atTime(f.getHoraFin())))
                .toList())
            .orElse(List.of());
    }

    private static boolean vigente(HorarioVersion v, LocalDate fecha) {
        return !v.getVigenteDesde().isAfter(fecha) && (v.getVigenteHasta() == null || !v.getVigenteHasta().isBefore(fecha));
    }
}
