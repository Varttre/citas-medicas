package pe.consultorio.citas.exception;

/**
Lo pedido no existe o la persona no tiene derecho a verlo. Se muestra como una página 404 en ambos casos, para no
revelar que existe algo ajeno (por ejemplo, la cita de otro médico).
*/
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String detalleParaElLog) {
        super(detalleParaElLog);
    }
}
