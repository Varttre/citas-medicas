package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.util.List;

/**
Las cuatro métricas del tablero para un rango de fechas. Los porcentajes son null cuando no hay casos para
calcularlos. {@code minutosRecuperados}: minutos de horarios liberados que volvieron a ocuparse; {@code abiertasAhora}:
horarios liberados que siguen esperando a alguien.
*/
public record Metricas(LocalDate desde, LocalDate hasta,
        long cubiertas, long expiradas, Integer recuperadosPct,
        long realizadas, long noAsistio, Integer noAsistenciaPct, List<NoAsistenciaMedico> porMedico,
        long indicadasAgendadas, long indicadasCerradas, Integer indicadasPct,
        long citasConEventos, double reprogramacionesPromedio, long reprogramacionesMax, long[] citasPorReprogramaciones,
        long minutosRecuperados, long abiertasAhora, List<NoAsistenciaGrupo> porEspecialidad,
        List<NoAsistenciaGrupo> porHora) {

    /** Tasa de no asistencia de un médico en el rango. */
    public record NoAsistenciaMedico(String medico, String especialidad, long realizadas, long noAsistio, Integer pct) {
    }

    /** Tasa de no asistencia de un grupo (una especialidad o una hora de inicio, por ejemplo «09:00»). */
    public record NoAsistenciaGrupo(String grupo, long realizadas, long noAsistio, Integer pct) {
    }
}
