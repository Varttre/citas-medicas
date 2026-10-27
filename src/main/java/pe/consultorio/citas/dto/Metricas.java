package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.util.List;

/**
Las cuatro métricas del tablero para un rango de fechas. Los porcentajes son null cuando no hay casos para
calcularlos.
*/
public record Metricas(LocalDate desde, LocalDate hasta,
        long cubiertas, long expiradas, Integer recuperadosPct,
        long realizadas, long noAsistio, Integer noAsistenciaPct, List<NoAsistenciaMedico> porMedico,
        long indicadasAgendadas, long indicadasCerradas, Integer indicadasPct,
        long citasConEventos, double reprogramacionesPromedio, long reprogramacionesMax, long[] citasPorReprogramaciones) {

    /** Tasa de no asistencia de un médico en el rango. */
    public record NoAsistenciaMedico(String medico, String especialidad, long realizadas, long noAsistio, Integer pct) {
    }
}
