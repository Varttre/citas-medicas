package pe.consultorio.citas.exception;

/**
Un cambio de estado se intentó fuera de su plazo. El mensaje dice la hora concreta, por ejemplo «Ya pasó el plazo
para cancelar esta cita (hasta las 08:30 del 12/11).».
*/
public class PlazoVencidoException extends ReglaNegocioException {

    public PlazoVencidoException(String mensajeParaUsuario) {
        super(mensajeParaUsuario);
    }
}
