package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaPaciente;
import pe.consultorio.citas.dto.CitaReprogramable;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
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
    private final Clock reloj;

    public PortalPacienteService(MedicoEspecialidadRepository medicoEspecialidades, CitaRepository citas,
            CuentaRepository cuentas, PreferenciaDisponibilidadRepository preferencias, ContactoService contacto,
            Clock reloj) {
        this.medicoEspecialidades = medicoEspecialidades;
        this.citas = citas;
        this.cuentas = cuentas;
        this.preferencias = preferencias;
        this.contacto = contacto;
        this.reloj = reloj;
    }

    /**
    La cita que la cuenta quiere reprogramar. La cuenta la ve si es de su paciente propio o si ella la agendó; si
    no, se trata como inexistente (404). Solo se reprograma una cita programada o confirmada que aún no empieza.
    */
    @Transactional(readOnly = true)
    public CitaReprogramable reprogramable(Long idCuenta, Long idCita) {
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
        if (!fila(c, LocalDateTime.now(reloj)).cancelable()) {
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

    /** Citas del paciente que aún no ocurren y no están canceladas, de la más próxima a la más lejana. */
    public List<CitaPaciente> proximas(Long idPaciente) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return citas.delPaciente(idPaciente).stream()
            .filter(c -> c.getFin().isAfter(ahora) && !CitaRepository.CANCELADAS.contains(c.getEstado()))
            .sorted(Comparator.comparing(Cita::getInicio))
            .map(c -> fila(c, ahora))
            .toList();
    }

    /** Citas ya ocurridas o canceladas, de la más reciente a la más antigua. */
    public List<CitaPaciente> pasadas(Long idPaciente) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return citas.delPaciente(idPaciente).stream()
            .filter(c -> !c.getFin().isAfter(ahora) || CitaRepository.CANCELADAS.contains(c.getEstado()))
            .map(c -> fila(c, ahora))
            .toList();
    }

    private static CitaPaciente fila(Cita c, LocalDateTime ahora) {
        boolean cancelable = (c.getEstado() == EstadoCita.PROGRAMADA || c.getEstado() == EstadoCita.CONFIRMADA)
            && c.getInicio().isAfter(ahora);
        return new CitaPaciente(c.getIdCita(), c.getInicio(), c.getFin(),
            c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getEspecialidad().getNombre(),
            c.getTipoConsulta().getNombre(), c.getModalidad() == null ? "" : c.getModalidad().name(),
            PacienteService.textoEstado(c.getEstado()), cancelable);
    }
}
