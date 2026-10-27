package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.VentanaPlazo;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.PlazoTransicion;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.PlazoVencidoException;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;

/**
Cambios de estado de una cita. Cada transición comprueba el estado de origen y su plazo (tabla plazo_transicion), y
deja un evento en cita_evento dentro de la misma transacción. Un médico solo actúa sobre sus propias citas.
*/
@Service
public class CitaEstadoService {

    private static final Set<EstadoCita> VIGENTES = EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm 'del' dd/MM");

    private final CitaRepository citas;
    private final CitaEventoRepository eventos;
    private final CuentaRepository cuentas;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public CitaEstadoService(CitaRepository citas, CitaEventoRepository eventos, CuentaRepository cuentas,
            ConfiguracionService configuracion, Clock reloj) {
        this.citas = citas;
        this.eventos = eventos;
        this.cuentas = cuentas;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /** Confirmación desde la cola de llamadas: se completa con la cola de confirmaciones (B2). */
    public void confirmar(Long idCita, Long idLlamada, Long actor) {
        throw new UnsupportedOperationException("Pendiente: B2");
    }

    /** El paciente cancela hasta la hora de inicio. Si la registra el personal, el motivo es obligatorio. */
    @Transactional
    public void cancelarPorPaciente(Long idCita, Long actor, String motivo) {
        Cita c = cita(idCita, actor);
        Cuenta quien = cuenta(actor);
        if (quien != null && quien.getRol().getNombre() != NombreRol.PACIENTE && vacio(motivo)) {
            throw new ReglaNegocioException("Indica el motivo de la cancelación.");
        }
        exigirEstado(c, VIGENTES, "Esta cita ya no se puede cancelar.");
        exigirPlazo(c, Transicion.CANCELADA_POR_PACIENTE, "cancelar esta cita");
        cambiar(c, EstadoCita.CANCELADA_POR_PACIENTE, actor, motivo);
    }

    /** El consultorio cancela (médico, recepción o administrador), siempre con motivo. */
    @Transactional
    public void cancelarPorClinica(Long idCita, Long actor, String motivo) {
        Cita c = cita(idCita, actor);
        if (vacio(motivo)) {
            throw new ReglaNegocioException("Indica el motivo de la cancelación.");
        }
        exigirEstado(c, VIGENTES, "Esta cita ya no se puede cancelar.");
        cambiar(c, EstadoCita.CANCELADA_POR_CLINICA, actor, motivo);
    }

    /** Recepción registra que el paciente llegó (desde 30 minutos antes hasta el fin del día). */
    @Transactional
    public void marcarLlegada(Long idCita, Long actor) {
        Cita c = cita(idCita, actor);
        exigirEstado(c, VIGENTES, "Solo se registra la llegada de una cita programada o confirmada.");
        exigirPlazo(c, Transicion.ASISTIO, "registrar la llegada");
        c.setHoraLlegada(ahora());
        cambiar(c, EstadoCita.ASISTIO, actor, null);
        evento(c, TipoEvento.LLEGADA, null, null, actor, null);
    }

    /** El médico empieza la atención de un paciente que ya llegó. */
    @Transactional
    public void iniciarAtencion(Long idCita, Long actor) {
        Cita c = cita(idCita, actor);
        if (c.getEstado() != EstadoCita.ASISTIO) {
            throw new ReglaNegocioException("Recepción aún no registró la llegada del paciente.");
        }
        if (c.getInicioReal() == null) {
            c.setInicioReal(ahora());
            evento(c, TipoEvento.INICIO_ATENCION, null, null, actor, null);
        }
    }

    /** El médico termina la atención: la cita queda REALIZADA. */
    @Transactional
    public void finalizarAtencion(Long idCita, Long actor) {
        Cita c = cita(idCita, actor);
        if (c.getEstado() != EstadoCita.ASISTIO || c.getInicioReal() == null) {
            throw new ReglaNegocioException("Primero inicia la atención.");
        }
        exigirPlazo(c, Transicion.REALIZADA, "finalizar la atención");
        LocalDateTime ahora = ahora();
        c.setFinReal(ahora.isAfter(c.getInicioReal()) ? ahora : c.getInicioReal().plusMinutes(1));
        cambiar(c, EstadoCita.REALIZADA, actor, null);
        evento(c, TipoEvento.FIN_ATENCION, null, null, actor, null);
    }

    /** No asistió: desde 5 minutos después del inicio hasta el fin del día. */
    @Transactional
    public void marcarNoAsistio(Long idCita, Long actor) {
        Cita c = cita(idCita, actor);
        exigirEstado(c, VIGENTES, "Solo una cita programada o confirmada puede marcarse como no asistida.");
        exigirPlazo(c, Transicion.NO_ASISTIO, "marcar la inasistencia");
        cambiar(c, EstadoCita.NO_ASISTIO, actor, null);
    }

    /** El paciente llegó pero se fue sin ser atendido: ASISTIO → NO_ASISTIO con motivo. */
    @Transactional
    public void marcarRetiro(Long idCita, Long actor, String motivo) {
        Cita c = cita(idCita, actor);
        if (vacio(motivo)) {
            throw new ReglaNegocioException("Indica el motivo del retiro.");
        }
        if (c.getEstado() != EstadoCita.ASISTIO || c.getInicioReal() != null) {
            throw new ReglaNegocioException("Solo se registra el retiro de un paciente que llegó y aún no fue atendido.");
        }
        exigirPlazo(c, Transicion.RETIRO, "registrar el retiro");
        cambiar(c, EstadoCita.NO_ASISTIO, actor, motivo);
    }

    /** Cambio de duración de una cita ya agendada: se completa junto con la agenda de recepción. */
    public void ajustarDuracion(Long idCita, int nuevaDuracionMin, Long actor) {
        throw new UnsupportedOperationException("Pendiente: ajuste de duración");
    }

    /** Indicaciones del médico, que verá el paciente. */
    @Transactional
    public void guardarIndicaciones(Long idCita, String texto, Long actor) {
        cita(idCita, actor).setIndicaciones(vacio(texto) ? null : texto.trim());
    }

    /** Observaciones administrativas de recepción. */
    @Transactional
    public void guardarIndicacionesAdmin(Long idCita, String texto, Long actor) {
        cita(idCita, actor).setIndicacionesAdmin(vacio(texto) ? null : texto.trim());
    }

    /**
    Ventana en la que se permite la transición, calculada desde la hora de inicio de la cita. hasta_fin_dia = 0 llega
    hasta el inicio del día siguiente, y 1 hasta el inicio de dos días después.
    */
    public VentanaPlazo ventana(Cita cita, Transicion t) {
        PlazoTransicion p = configuracion.plazo(t);
        LocalDateTime desde = p.getMinutosDesde() == null ? null : cita.getInicio().plusMinutes(p.getMinutosDesde());
        LocalDateTime hasta;
        if (p.getHastaFinDia() != null) {
            hasta = cita.getInicio().toLocalDate().plusDays(1L + p.getHastaFinDia()).atStartOfDay();
        } else {
            hasta = p.getMinutosHasta() == null ? null : cita.getInicio().plusMinutes(p.getMinutosHasta());
        }
        return new VentanaPlazo(desde, hasta);
    }

    // ---------------------------------------------------------------- Apoyo

    /** La cita; si el actor es médico y la cita no es suya, se trata como inexistente. */
    private Cita cita(Long idCita, Long actor) {
        Cita c = citas.findById(idCita)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
        Cuenta quien = cuenta(actor);
        if (quien != null && quien.getRol().getNombre() == NombreRol.MEDICO
                && !c.getMedico().getCuenta().getIdCuenta().equals(quien.getIdCuenta())) {
            throw new RecursoNoEncontradoException("Cita " + idCita + " de otro médico");
        }
        return c;
    }

    private Cuenta cuenta(Long actor) {
        return actor == null ? null : cuentas.findById(actor).orElse(null);
    }

    private static void exigirEstado(Cita c, Set<EstadoCita> permitidos, String mensaje) {
        if (!permitidos.contains(c.getEstado())) {
            throw new ReglaNegocioException(mensaje);
        }
    }

    private void exigirPlazo(Cita c, Transicion t, String accion) {
        VentanaPlazo v = ventana(c, t);
        LocalDateTime ahora = ahora();
        if (v.desde() != null && ahora.isBefore(v.desde())) {
            throw new PlazoVencidoException("Aún no se puede " + accion + ": se habilita a las "
                + v.desde().format(HORA) + ".");
        }
        if (v.hasta() != null && !ahora.isBefore(v.hasta())) {
            throw new PlazoVencidoException("Ya pasó el plazo para " + accion + " (hasta las "
                + v.hasta().format(HORA) + ").");
        }
    }

    private void cambiar(Cita c, EstadoCita nuevo, Long actor, String motivo) {
        EstadoCita anterior = c.getEstado();
        c.setEstado(nuevo);
        citas.saveAndFlush(c);
        evento(c, TipoEvento.CAMBIO_ESTADO, anterior, nuevo, actor, motivo);
    }

    private void evento(Cita c, TipoEvento tipo, EstadoCita anterior, EstadoCita nuevo, Long actor, String motivo) {
        CitaEvento e = new CitaEvento();
        e.setCita(c);
        e.setCuenta(actor == null ? null : cuentas.getReferenceById(actor));
        e.setTipoEvento(tipo);
        e.setEstadoAnterior(anterior);
        e.setEstadoNuevo(nuevo);
        e.setMotivo(vacio(motivo) ? null : motivo.trim());
        e.setFechaHora(ahora());
        eventos.save(e);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
