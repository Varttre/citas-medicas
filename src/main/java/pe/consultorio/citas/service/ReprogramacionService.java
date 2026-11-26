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
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidadId;
import pe.consultorio.citas.entity.MedicoTipoConsultaId;
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
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Mueve una cita a otro horario conservando su identificador. No hay plazo de antelación: basta con que la cita aún no
haya empezado. Se conservan el médico, el tipo, la duración y la modalidad; la cita vuelve a PROGRAMADA (pierde la
confirmación) y queda un evento REPROGRAMACION con los horarios anterior y nuevo. El horario anterior se ofrece como
oportunidad y, si el nuevo cae en un horario liberado, este queda cubierto.
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
    private final OportunidadService oportunidadService;
    private final ConfiguracionService configuracion;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final MedicoTipoConsultaRepository medicoTipos;
    private final Clock reloj;

    public ReprogramacionService(CitaRepository citas, CitaEventoRepository eventos, CuentaRepository cuentas,
            OportunidadRepository oportunidades, BloqueoTemporalRepository bloqueos,
            DisponibilidadService disponibilidad, PreferenciaService preferencias,
            OportunidadService oportunidadService, ConfiguracionService configuracion,
            MedicoEspecialidadRepository medicoEspecialidades, MedicoTipoConsultaRepository medicoTipos, Clock reloj) {
        this.medicoEspecialidades = medicoEspecialidades;
        this.medicoTipos = medicoTipos;
        this.citas = citas;
        this.eventos = eventos;
        this.cuentas = cuentas;
        this.oportunidades = oportunidades;
        this.bloqueos = bloqueos;
        this.disponibilidad = disponibilidad;
        this.preferencias = preferencias;
        this.oportunidadService = oportunidadService;
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
        mover(cita(idCita, actor), nuevoInicio, null, actor, null);
    }

    /**
    Mueve la cita a {@code nuevoInicio}; si {@code medicoDestino} no es nulo y es otro médico, la cita pasa a su agenda
    (adelanto por un cupo de la especialidad). Primero se cubren los horarios liberados que ocupa el horario nuevo y
    después se libera el anterior: al revés, si ambos son contiguos, el bloque liberado absorbería al que se está
    cubriendo y este quedaría como reemplazado en vez de cubierto (la recuperación no se contaría).
    */
    private void mover(Cita c, LocalDateTime nuevoInicio, Medico medicoDestino, Long actor, String motivo) {
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
        Medico medicoAnterior = c.getMedico();
        Medico destino = medicoDestino != null ? medicoDestino : medicoAnterior;
        boolean cambiaMedico = !destino.getIdMedico().equals(medicoAnterior.getIdMedico());
        Long idMedico = destino.getIdMedico();

        RangeSet<LocalDateTime> libres = TreeRangeSet.create(
            disponibilidad.bloquesLibresDelDia(idMedico, nuevoInicio.toLocalDate()));
        if (!cambiaMedico && inicioAnterior.toLocalDate().equals(nuevoInicio.toLocalDate())) {
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
        c.setMedico(destino);
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
        ev.setMotivo(motivo);
        ev.setFechaHora(ahora);
        eventos.save(ev);
        oportunidadService.registrarCobertura(c);
        oportunidadService.liberar(c, medicoAnterior, inicioAnterior, finAnterior);
        if (actor != null) {
            bloqueos.borrarDeCuenta(actor);
        }
    }

    /**
    Adelanta la cita a un horario liberado que el paciente aceptó por teléfono: el horario debe estar dentro de un
    bloque liberado aún abierto. Si el bloque es de otro médico, el paciente tiene que haber aceptado toda la
    especialidad y ese médico debe atender la especialidad y el tipo de consulta de la cita; la cita pasa a su agenda
    con la misma duración. Su preferencia de disponibilidad inmediata queda consumida.
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
        if (nuevoInicio == null || nuevoInicio.isBefore(o.getInicio())
                || nuevoInicio.plusMinutes(minutos).isAfter(o.getFin())) {
            throw new ReglaNegocioException("La cita no cabe en ese horario liberado.");
        }
        Medico destino = o.getMedico();
        String motivo = null;
        if (!destino.getIdMedico().equals(c.getMedico().getIdMedico())) {
            boolean aceptaEspecialidad = preferencias.vigenteDe(idCita)
                .map(p -> p.getAlcance() == AlcancePreferencia.ESPECIALIDAD).orElse(false);
            if (!aceptaEspecialidad
                    || !medicoEspecialidades.existsById(new MedicoEspecialidadId(destino.getIdMedico(),
                        c.getEspecialidad().getIdEspecialidad()))
                    || !medicoTipos.existsById(new MedicoTipoConsultaId(destino.getIdMedico(),
                        c.getTipoConsulta().getIdTipoConsulta()))) {
                throw new ReglaNegocioException("Este paciente no puede pasar a la agenda de ese médico.");
            }
            motivo = "Adelantada por cupo liberado: pasa de " + c.getMedico().getNombres() + " "
                + c.getMedico().getApellidos() + " a " + destino.getNombres() + " " + destino.getApellidos();
        }
        mover(c, nuevoInicio, destino, actor, motivo);
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
