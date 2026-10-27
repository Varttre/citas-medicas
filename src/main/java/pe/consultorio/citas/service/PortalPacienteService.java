package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaPaciente;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;

/** Consultas del portal del paciente: médicos para agendar y sus citas. */
@Service
@Transactional(readOnly = true)
public class PortalPacienteService {

    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final CitaRepository citas;
    private final Clock reloj;

    public PortalPacienteService(MedicoEspecialidadRepository medicoEspecialidades, CitaRepository citas,
            Clock reloj) {
        this.medicoEspecialidades = medicoEspecialidades;
        this.citas = citas;
        this.reloj = reloj;
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
