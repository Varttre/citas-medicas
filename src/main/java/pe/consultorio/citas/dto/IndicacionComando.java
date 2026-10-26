package pe.consultorio.citas.dto;

import java.time.LocalDate;

/**
Datos con los que el médico indica la siguiente cita de un paciente.

@param idTipoConsulta   tipo de consulta indicado
@param duracionMin      duración prevista, múltiplo del incremento
@param fechaRecomendada fecha sugerida para la cita
@param vigenteHasta     último día en que la indicación se puede agendar
@param indicaciones     texto libre para el paciente; puede ser null
*/
public record IndicacionComando(Short idTipoConsulta, int duracionMin, LocalDate fechaRecomendada,
        LocalDate vigenteHasta, String indicaciones) {
}
