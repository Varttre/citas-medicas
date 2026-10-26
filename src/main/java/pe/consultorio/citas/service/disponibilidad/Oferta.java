package pe.consultorio.citas.service.disponibilidad;

import java.time.LocalDateTime;

/**
Un horario que se puede ofrecer, dentro del bloque libre del que sale.

@param inicio       inicio de la cita ofrecida
@param fin          fin de la cita ofrecida
@param bloqueInicio inicio del bloque libre que la contiene
@param bloqueFin    fin del bloque libre que la contiene
@param residuoMin   minutos del bloque que quedan sin usar
@param calce        clasificación según el residuo
@param protegida    el horario se reserva para una cita larga (siempre false en el MVP)
*/
public record Oferta(LocalDateTime inicio, LocalDateTime fin, LocalDateTime bloqueInicio,
        LocalDateTime bloqueFin, int residuoMin, Calce calce, boolean protegida) {
}
