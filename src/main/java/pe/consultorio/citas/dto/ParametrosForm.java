package pe.consultorio.citas.dto;

/** Parámetros globales editables del consultorio. El incremento y la política de protección no se editan. */
public record ParametrosForm(Short minutosProtegidos, Short ventanaLiberacionH, Short bloqueoTemporalMin,
        Short nVentanaEstimacion, Short diasRecordarSugerencia, Short anticipacionConfirmH,
        Short vigenciaIndicadaDias, Boolean mostrarPenalizacion, String textoPenalizacion, Short idTipoWebDefecto,
        String whatsappNumero, Short maxIntentosLogin, Short minutosBloqueoLogin, Short minHorasAdelanto) {

    /** Sin el mínimo de horas de «Acepto ser llamado»: se conserva el que hay. */
    public ParametrosForm(Short minutosProtegidos, Short ventanaLiberacionH, Short bloqueoTemporalMin,
            Short nVentanaEstimacion, Short diasRecordarSugerencia, Short anticipacionConfirmH,
            Short vigenciaIndicadaDias, Boolean mostrarPenalizacion, String textoPenalizacion, Short idTipoWebDefecto,
            String whatsappNumero, Short maxIntentosLogin, Short minutosBloqueoLogin) {
        this(minutosProtegidos, ventanaLiberacionH, bloqueoTemporalMin, nVentanaEstimacion, diasRecordarSugerencia,
            anticipacionConfirmH, vigenciaIndicadaDias, mostrarPenalizacion, textoPenalizacion, idTipoWebDefecto,
            whatsappNumero, maxIntentosLogin, minutosBloqueoLogin, null);
    }
}
