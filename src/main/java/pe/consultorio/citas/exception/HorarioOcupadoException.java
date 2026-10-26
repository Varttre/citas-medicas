package pe.consultorio.citas.exception;

/**
El horario pedido se cruza con otra cita del mismo médico. Normalmente se lanza cuando PostgreSQL rechaza la
escritura por la restricción contra solapes (ver {@link ErroresBaseDatos#esSolape(Throwable)}).
*/
public class HorarioOcupadoException extends ReglaNegocioException {

    public static final String MENSAJE = "Ese horario ya fue ocupado. Elige otro.";

    public HorarioOcupadoException() {
        super(MENSAJE);
    }

    /** Para casos con un mensaje más preciso, por ejemplo al alargar una cita. */
    public HorarioOcupadoException(String mensajeParaUsuario) {
        super(mensajeParaUsuario);
    }
}
