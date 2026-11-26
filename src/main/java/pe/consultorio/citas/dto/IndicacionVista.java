package pe.consultorio.citas.dto;

import java.time.LocalDate;

/** Una cita indicada por el médico, lista para mostrarla (cola de pendientes o nueva cita desde la indicación). */
public record IndicacionVista(Long idIndicacion, Long idPaciente, String paciente, String telefono, String medico,
        Short idTipoConsulta, String tipo, int duracion, LocalDate fechaRecomendada, LocalDate vigenteHasta,
        long diasRestantes, String indicaciones, String estado) {
}
