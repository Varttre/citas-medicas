package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaPaciente;
import pe.consultorio.citas.dto.CitaReprogramable;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;

/** Consultas del portal del paciente: médicos para agendar y sus citas. */
@Service
@Transactional(readOnly = true)
public class PortalPacienteService {

    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final CitaRepository citas;
    private final CuentaRepository cuentas;
    private final PreferenciaDisponibilidadRepository preferencias;
    private final ContactoService contacto;
    private final CitaEstadoService estados;
    private final Clock reloj;

    public PortalPacienteService(MedicoEspecialidadRepository medicoEspecialidades, CitaRepository citas,
            CuentaRepository cuentas, PreferenciaDisponibilidadRepository preferencias, ContactoService contacto,
            CitaEstadoService estados, Clock reloj) {
        this.medicoEspecialidades = medicoEspecialidades;
        this.citas = citas;
        this.cuentas = cuentas;
        this.preferencias = preferencias;
        this.contacto = contacto;
        this.estados = estados;
        this.reloj = reloj;
    }

    /**
    La cita que la cuenta quiere reprogramar. La cuenta la ve si es de su paciente propio o si ella la agendó; si
    no, se trata como inexistente (404). Solo se reprograma una cita programada o confirmada que aún no empieza.
    */
    @Transactional(readOnly = true)
    public CitaReprogramable reprogramable(Long idCuenta, Long idCita) {
        Cita c = visible(idCuenta, idCita);
        if (!vigente(c, LocalDateTime.now(reloj))) {
            throw new ReglaNegocioException("Esta cita ya no se puede reprogramar.");
        }
        PreferenciaDatos preferencia = preferencias.findByCitaIdCita(idCita)
            .filter(p -> p.getRevocadoEn() == null)
            .map(p -> new PreferenciaDatos(p.getAlcance(), p.getMinLlegadaMin(), p.getDiasPrevios()))
            .orElse(null);
        return new CitaReprogramable(c.getIdCita(), c.getMedico().getIdMedico(),
            c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getEspecialidad().getNombre(),
            c.getTipoConsulta().getNombre(), (int) Duration.between(c.getInicio(), c.getFin()).toMinutes(),
            c.getModalidad(), c.getInicio(), c.getFin(),
            c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            PacienteService.textoEstado(c.getEstado()), contacto.telefonoDe(c), preferencia);
    }

    /** Médicos activos que atienden la especialidad (o todos, si no se indica), por nombre. */
    public List<MedicoOpcion> medicos(Short idEspecialidad) {
        return medicoEspecialidades.findAll().stream()
            .filter(me -> me.getMedico().isActivo())
            .filter(me -> idEspecialidad == null || me.getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .map(me -> new MedicoOpcion(me.getMedico().getIdMedico(),
                me.getMedico().getNombres() + " " + me.getMedico().getApellidos(),
                me.getEspecialidad().getIdEspecialidad(), me.getEspecialidad().getNombre()))
            .sorted(Comparator.comparing(MedicoOpcion::nombre))
            .toList();
    }

    /**
    Citas que ve la cuenta y aún no ocurren ni están canceladas, de la más próxima a la más lejana: todas las de su
    paciente propio (las agendara quien las agendara) y las que ella agendó para otras personas.
    */
    @Transactional(readOnly = true)
    public List<CitaPaciente> proximas(Long idCuenta) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return visibles(idCuenta).stream()
            .filter(c -> c.getFin().isAfter(ahora) && !CitaRepository.CANCELADAS.contains(c.getEstado()))
            .sorted(Comparator.comparing(Cita::getInicio))
            .map(c -> fila(c, idCuenta, ahora))
            .toList();
    }

    /** Las demás citas que ve la cuenta (ya ocurridas o canceladas), de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<CitaPaciente> pasadas(Long idCuenta) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return visibles(idCuenta).stream()
            .filter(c -> !c.getFin().isAfter(ahora) || CitaRepository.CANCELADAS.contains(c.getEstado()))
            .sorted(Comparator.comparing(Cita::getInicio).reversed())
            .map(c -> fila(c, idCuenta, ahora))
            .toList();
    }

    /**
    La cita, si la cuenta la ve (es de su paciente propio o ella la agendó); si no, se trata como inexistente (404).
    */
    @Transactional(readOnly = true)
    public Cita visible(Long idCuenta, Long idCita) {
        Cuenta cuenta = cuentas.findById(idCuenta)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta inexistente " + idCuenta));
        Cita c = citas.findById(idCita)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
        boolean propia = cuenta.getPacientePropio() != null
            && c.getPaciente().getIdPaciente().equals(cuenta.getPacientePropio().getIdPaciente());
        boolean agendadaPorMi = c.getCuentaTitular() != null && c.getCuentaTitular().getIdCuenta().equals(idCuenta);
        if (!propia && !agendadaPorMi) {
            throw new RecursoNoEncontradoException("Cita " + idCita + " de otro paciente");
        }
        return c;
    }

    /** Si la cita aún admite cambios del paciente (cancelar, reprogramar, preferencia): vigente y sin empezar. */
    private static boolean vigente(Cita c, LocalDateTime ahora) {
        return (c.getEstado() == EstadoCita.PROGRAMADA || c.getEstado() == EstadoCita.CONFIRMADA)
            && c.getInicio().isAfter(ahora);
    }

    private List<Cita> visibles(Long idCuenta) {
        Cuenta cuenta = cuentas.findById(idCuenta)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta inexistente " + idCuenta));
        Map<Long, Cita> todas = new LinkedHashMap<>();
        if (cuenta.getPacientePropio() != null) {
            citas.delPaciente(cuenta.getPacientePropio().getIdPaciente()).forEach(c -> todas.put(c.getIdCita(), c));
        }
        citas.delTitular(idCuenta).forEach(c -> todas.putIfAbsent(c.getIdCita(), c));
        return List.copyOf(todas.values());
    }

    private CitaPaciente fila(Cita c, Long idCuenta, LocalDateTime ahora) {
        Cuenta cuenta = cuentas.getReferenceById(idCuenta);
        boolean tercero = cuenta.getPacientePropio() == null
            || !c.getPaciente().getIdPaciente().equals(cuenta.getPacientePropio().getIdPaciente());
        PreferenciaDatos preferencia = preferencias.findByCitaIdCita(c.getIdCita())
            .filter(p -> p.getRevocadoEn() == null)
            .map(p -> new PreferenciaDatos(p.getAlcance(), p.getMinLlegadaMin(), p.getDiasPrevios()))
            .orElse(null);
        return new CitaPaciente(c.getIdCita(), c.getInicio(), c.getFin(),
            c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getEspecialidad().getNombre(),
            c.getTipoConsulta().getNombre(), c.getModalidad() == null ? "" : c.getModalidad().name(),
            PacienteService.textoEstado(c.getEstado()), vigente(c, ahora), tercero, c.getIndicaciones(),
            c.getIndicacionesAdmin(), preferencia, estados.ventana(c, Transicion.CANCELADA_POR_PACIENTE).hasta());
    }
}
