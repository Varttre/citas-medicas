package pe.consultorio.citas.exception;

import java.sql.SQLException;

/** Reconoce errores concretos de PostgreSQL dentro de las excepciones que envuelven Spring y Hibernate. */
public final class ErroresBaseDatos {

    /** Código SQLState de PostgreSQL para la violación de una restricción EXCLUDE (dos citas que se solapan). */
    public static final String VIOLACION_EXCLUSION = "23P01";

    private ErroresBaseDatos() {
    }

    /**
    Indica si el error, o alguna de sus causas, es un rechazo de PostgreSQL por solape de horarios. Spring envuelve
    el {@link SQLException} original en varias capas, por eso se recorre toda la cadena de causas.
    */
    public static boolean esSolape(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && VIOLACION_EXCLUSION.equals(sql.getSQLState())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
