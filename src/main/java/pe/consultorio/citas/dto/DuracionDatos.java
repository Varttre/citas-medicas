package pe.consultorio.citas.dto;

/**
Duración que usa el médico para un tipo de consulta.

@param idTipoConsulta      identificador del tipo
@param tipo                nombre del tipo
@param duracionConsultorio duración por defecto del consultorio para ese tipo
@param duracionMin         duración que usa este médico
*/
public record DuracionDatos(Short idTipoConsulta, String tipo, int duracionConsultorio, int duracionMin) {
}
