package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.exception.ErroresBaseDatos;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.BloqueoTemporalRepository;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Mueve una cita a otro horario conservando su identificador. No hay plazo de antelación: basta con que la cita aún no
haya empezado. Se conservan el médico, el tipo, la duración y la modalidad; la cita vuelve a PROGRAMADA (pierde la
confirmación) y queda un evento REPROGRAMACION con los horarios anterior y nuevo.
*/
@Service
public class ReprogramacionService {

    private static final Set<EstadoCita> VIGENTES = EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA);

    private final CitaRepository citas;
    private final CitaEventoRepository eventos;
    private final CuentaRepository cuentas;
    private final OportunidadRepository oportunidades;
    private final BloqueoTemporalRepository bloqueos;
    private final DisponibilidadService disponibilidad;
    private final PreferenciaService preferencias;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public ReprogramacionService(CitaRepository citas, CitaEventoRepository eventos, CuentaRepository cuentas,
            OportunidadRepository oportunidades, BloqueoTemporalRepository bloqueos,
            DisponibilidadService disponibilidad, PreferenciaService preferencias,
            ConfiguracionService configuracion, Clock reloj) {
        this.citas = citas;
        this.eventos = eventos;
        this.cuentas = cuentas;
        this.oportunidades = oportunidades;
        this.bloqueos = bloqueos;
        this.disponibilidad = disponibilidad;
        this.preferencias = preferencias;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /**
    Cambia el horario de la cita. El nuevo horario debe caer en la rejilla, en el futuro y dentro de un bloque libre
    del médico (puede solaparse con el horario actual de la misma cita). Si otra cita lo ocupa, PostgreSQL rechaza el
    cambio y la cita queda como estaba.
    @throws HorarioOcupadoException si el horario ya no está libre
    */
    @Transactional
    public void reprogramar(Long idCita, LocalDateTime nuevoInicio, Long actor) {
        Cita c = cita(idCita, actor);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (!VIGENTES.contains(c.getEstado()) || !c.getInicio().isAfter(ahora)) {
            throw new ReglaNegocioException("Esta cita ya no se puede reprogramar.");
        }
        if (nuevoInicio == null || !Rejilla.enRejilla(nuevoInicio, configuracion.incremento())) {
            throw new ReglaNegocioException("Elige uno de los horarios disponibles.");
        }
        if (!nuevoInicio.isAfter(ahora)) {
            throw new ReglaNegocioException("Elige un horario futuro.");
        }
        if (nuevoInicio.equals(c.getInicio())) {
            throw new ReglaNegocioException("Ese ya es el horario de la cita. Elige otro.");
        }
        LocalDateTime inicioAnterior = c.getInicio();
        LocalDateTime finAnterior = c.getFin();
        LocalDateTime nuevoFin = nuevoInicio.plusMinutes(java.time.Duration.between(inicioAnterior, finAnterior)
            .toMinutes());
        Long idMedico = c.getMedico().getIdMedico();

        RangeSet<LocalDateTime> libres = TreeRangeSet.create(
            disponibilidad.bloquesLibresDelDia(idMedico, nuevoInicio.toLocalDate()));
        if (inicioAnterior.toLocalDate().equals(nuevoInicio.toLocalDate())) {
            libres.add(Range.closedOpen(inicioAnterior, finAnterior));
        }
        if (!libres.encloses(Range.closedOpen(nuevoInicio, nuevoFin))) {
            throw new HorarioOcupadoException();
        }
        boolean retenidoPorOtro = bloqueos.vigentesQueCruzan(idMedico, nuevoInicio, nuevoFin, ahora).stream()
            .anyMatch(b -> actor == null || !b.getCuenta().getIdCuenta().equals(actor));
        if (retenidoPorOtro) {
            throw new HorarioOcupadoException();
        }

        EstadoCita estadoAnterior = c.getEstado();
        c.setInicio(nuevoInicio);
        c.setFin(nuevoFin);
        c.setEstado(EstadoCita.PROGRAMADA);
        try {
            citas.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            if (ErroresBaseDatos.esSolape(e)) {
                throw new HorarioOcupadoException();
            }
            throw e;
        }

        CitaEvento ev = new CitaEvento();
        ev.setCita(c);
        ev.setCuenta(actor == null ? null : cuentas.getReferenceById(actor));
        ev.setTipoEvento(TipoEvento.REPROGRAMACION);
        ev.setEstadoAnterior(estadoAnterior);
        ev.setEstadoNuevo(EstadoCita.PROGRAMADA);
        ev.setInicioAnterior(inicioAnterior);
        ev.setFinAnterior(finAnterior);
        ev.setInicioNuevo(nuevoInicio);
        ev.setFinNuevo(nuevoFin);
        ev.setFechaHora(ahora);
        eventos.save(ev);
        if (actor != null) {
            bloqueos.borrarDeCuenta(actor);
        }
    }

    /**
    Adelanta la cita a un horario liberado que el paciente aceptó por teléfono: el horario debe estar dentro de un
    bloque liberado aún abierto del mismo médico. Su preferencia de disponibilidad inmediata queda consumida.
    */
    @Transactional
    public void adelantarPorCupo(Long idCita, Long idOportunidad, LocalDateTime nuevoInicio, Long actor) {
        Oportunidad o = oportunidades.findById(idOportunidad)
            .orElseThrow(() -> new RecursoNoEncontradoException("Oportunidad inexistente " + idOportunidad));
        Cita c = cita(idCita, actor);
        if (o.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException("Este horario liberado ya no está disponible.");
        }
        long minutos = java.time.Duration.between(c.getInicio(), c.getFin()).toMinutes();
        if (!o.getMedico().getIdMedico().equals(c.getMedico().getIdMedico()) || nuevoInicio == null
                || nuevoInicio.isBefore(o.getInicio()) || nuevoInicio.plusMinutes(minutos).isAfter(o.getFin())) {
            throw new ReglaNegocioException("La cita no cabe en ese horario liberado.");
        }
        reprogramar(idCita, nuevoInicio, actor);
        preferencias.consumir(idCita, actor);
    }

    /** La cita; si el actor es médico y la cita no es suya, se trata como inexistente. */
    private Cita cita(Long idCita, Long actor) {
        Cita c = citas.findById(idCita)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
        Cuenta quien = actor == null ? null : cuentas.findById(actor).orElse(null);
        if (quien != null && quien.getRol().getNombre() == NombreRol.MEDICO
                && !c.getMedico().getCuenta().getIdCuenta().equals(quien.getIdCuenta())) {
            throw new RecursoNoEncontradoException("Cita " + idCita + " de otro médico");
        }
        return c;
    }
}
