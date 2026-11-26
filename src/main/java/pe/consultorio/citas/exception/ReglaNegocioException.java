package pe.consultorio.citas.exception;

/**
Una operación no se puede hacer porque rompe una regla del consultorio. El mensaje ya está redactado para la persona
que usa el sistema, en español, y el controlador lo muestra tal cual. {@code campo}, si viene, es el nombre del campo
del formulario que hay que corregir, para marcarlo en pantalla sin perder lo escrito.
*/
public class ReglaNegocioException extends RuntimeException {

    private final String campo;

    public ReglaNegocioException(String mensajeParaUsuario) {
        this(mensajeParaUsuario, null);
    }

    public ReglaNegocioException(String mensajeParaUsuario, String campo) {
        super(mensajeParaUsuario);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
