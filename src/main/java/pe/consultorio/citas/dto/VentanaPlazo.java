package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Intervalo de tiempo en el que se permite un cambio de estado de una cita.

@param desde primer instante permitido (incluido); null = sin límite inferior
@param hasta instante en que deja de permitirse (excluido); null = sin límite superior
*/
public record VentanaPlazo(LocalDateTime desde, LocalDateTime hasta) {

    /** Indica si {@code ahora} cae dentro de la ventana: {@code desde <= ahora < hasta}. */
    public boolean permite(LocalDateTime ahora) {
        boolean despuesDelInicio = desde == null || !ahora.isBefore(desde);
        boolean antesDelFin = hasta == null || ahora.isBefore(hasta);
        return despuesDelInicio && antesDelFin;
    }
}
