package pe.consultorio.citas.dto;

import pe.consultorio.citas.entity.ModalidadPermitida;

/**
Formulario de un tipo de consulta en la configuración. {@code incrementoMin} nulo = usa el incremento global. Las
casillas sin marcar llegan como null.
*/
public record TipoConsultaForm(String nombre, Short duracionDefecto, Short incrementoMin, Boolean ajusteMedico,
        Boolean ajusteRecepcion, Boolean reservableWeb, ModalidadPermitida modalidadPermitida, Boolean activo) {
}
