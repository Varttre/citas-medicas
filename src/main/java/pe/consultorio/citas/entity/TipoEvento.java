package pe.consultorio.citas.entity;

/**
Tipo de un registro del historial de la cita. Sus valores son los mismos que acepta la base de
datos.
*/
public enum TipoEvento {
    CREACION, CAMBIO_ESTADO, REPROGRAMACION, AJUSTE_DURACION,
    LLEGADA, INICIO_ATENCION, FIN_ATENCION
}
