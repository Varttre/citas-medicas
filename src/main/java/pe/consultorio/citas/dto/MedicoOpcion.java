package pe.consultorio.citas.dto;

/** Un médico que el paciente puede elegir en la nueva cita. */
public record MedicoOpcion(Long idMedico, String nombre, Short idEspecialidad, String especialidad) {
}
