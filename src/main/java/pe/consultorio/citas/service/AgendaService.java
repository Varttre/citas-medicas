package pe.consultorio.citas.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaAgenda;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.repository.CitaRepository;

/** Consultas de agenda del día para recepción (todos los médicos) y para el médico (solo las suyas). */
@Service
@Transactional(readOnly = true)
public class AgendaService {

    private final CitaRepository citas;

    public AgendaService(CitaRepository citas) {
        this.citas = citas;
    }

    /** Citas del día de todos los médicos, por médico y hora. {@code idMedico} opcional para filtrar. */
    public List<CitaAgenda> delDia(LocalDate fecha, Long idMedico) {
        return citas.delDia(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
            .filter(c -> idMedico == null || c.getMedico().getIdMedico().equals(idMedico))
            .map(AgendaService::fila)
            .sorted(Comparator.comparing(CitaAgenda::medico).thenComparing(CitaAgenda::inicio))
            .toList();
    }

    /** Citas del día del médico de la sesión. */
    public List<CitaAgenda> delMedico(Long idMedico, LocalDate fecha) {
        return delDia(fecha, idMedico);
    }

    private static CitaAgenda fila(Cita c) {
        return new CitaAgenda(c.getIdCita(), c.getMedico().getIdMedico(),
            c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getEspecialidad().getNombre(),
            c.getInicio(), c.getFin(), c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            c.getPaciente().getTipoDocumento() + " " + c.getPaciente().getNroDocumento(),
            c.getTipoConsulta().getNombre(), c.getModalidad().name(), c.getEstado().name(),
            PacienteService.textoEstado(c.getEstado()), c.getInicioReal() != null);
    }
}
