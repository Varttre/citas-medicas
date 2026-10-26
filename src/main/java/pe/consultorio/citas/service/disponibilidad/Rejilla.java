package pe.consultorio.citas.service.disponibilidad;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
Utilidades de la rejilla de tiempo: todos los inicios, fines y duraciones son múltiplos del incremento
(15 minutos por defecto). La rejilla se cuenta desde la medianoche: con incremento 15 los puntos válidos son
08:00, 08:15, 08:30…
*/
public final class Rejilla {

    private static final int MINUTOS_DEL_DIA = 24 * 60;

    private Rejilla() {
    }

    /** Indica si {@code t} cae exactamente en la rejilla (minutos múltiplo del incremento, sin segundos). */
    public static boolean enRejilla(LocalDateTime t, int incremento) {
        validar(incremento);
        return t.getSecond() == 0 && t.getNano() == 0 && minutoDelDia(t.toLocalTime()) % incremento == 0;
    }

    /**
    Lleva {@code t} al siguiente punto de la rejilla; si ya está en ella, la devuelve igual.
    Ejemplos con incremento 15: 10:07 → 10:15 · 10:15 → 10:15 · 10:15:30 → 10:30 · 23:50 → 00:00 del día siguiente.
    */
    public static LocalDateTime redondearArriba(LocalDateTime t, int incremento) {
        validar(incremento);
        LocalDateTime base = t.truncatedTo(ChronoUnit.MINUTES);
        if (base.isBefore(t)) {
            base = base.plusMinutes(1);   // 10:15:30 ya pasó las 10:15: cuenta desde 10:16
        }
        int resto = minutoDelDia(base.toLocalTime()) % incremento;
        return resto == 0 ? base : base.plusMinutes(incremento - resto);
    }

    /**
    Horas de {@code desde} a {@code hasta} (ambas incluidas) cada {@code incremento} minutos, para los selectores
    de hora. {@code horasDelDia(08:00, 09:00, 15)} → 08:00, 08:15, 08:30, 08:45, 09:00.
    */
    public static List<LocalTime> horasDelDia(LocalTime desde, LocalTime hasta, int incremento) {
        validar(incremento);
        List<LocalTime> horas = new ArrayList<>();
        int fin = minutoDelDia(hasta);
        // Se cuenta en minutos y no con LocalTime.plusMinutes, que daría la vuelta a la medianoche y no terminaría
        for (int m = minutoDelDia(desde); m <= fin && m < MINUTOS_DEL_DIA; m += incremento) {
            horas.add(LocalTime.of(m / 60, m % 60));
        }
        return horas;
    }

    private static int minutoDelDia(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static void validar(int incremento) {
        if (incremento <= 0) {
            throw new IllegalArgumentException("El incremento debe ser mayor que 0: " + incremento);
        }
    }
}
