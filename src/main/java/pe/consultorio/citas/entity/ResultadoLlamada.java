package pe.consultorio.citas.entity;

/**
Resultado de una llamada. Cada cola admite solo algunos valores (los valida la base). Sus valores
son los mismos que acepta la base de datos.
*/
public enum ResultadoLlamada {
    CONFIRMO, NO_CONTESTO, CANCELO, REPROGRAMO, NUMERO_INVALIDO,
    ACEPTO, RECHAZO, NO_PUEDE_LLEGAR, OTRO, AGENDO, NO_LE_INTERESA
}
