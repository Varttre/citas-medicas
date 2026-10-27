package pe.consultorio.citas.dto;

/** Una fila del resultado de búsqueda de pacientes. */
public record PacienteResumen(Long idPaciente, String nombreCompleto, String documento, String telefono) {
}
