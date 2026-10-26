package pe.consultorio.citas.exception;

/**
Una operación no se puede hacer porque rompe una regla del consultorio. El mensaje ya está redactado para la persona
que usa el sistema, en español, y el controlador lo muestra tal cual.
*/
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensajeParaUsuario) {
        super(mensajeParaUsuario);
    }
}
