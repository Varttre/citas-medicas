package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.util.List;

/**
El reporte del consultorio para un rango de fechas, con filtro opcional por médico y especialidad. Los porcentajes son
null cuando no hay casos para calcularlos.

Recuperación: {@code minutosRecuperados} y {@code horariosRecuperados} cuentan solo lo que cae dentro del horario que
realmente se soltó (una cita cancelada, movida o acortada), no todo el bloque libre que se ofreció a su alrededor.
{@code horariosSoltados} y {@code minutosSoltados} son la base de comparación; {@code abiertasAhora}, los horarios
liberados que siguen esperando a alguien.

@param filtroMedico       nombre del médico filtrado, o null si son todos
@param filtroEspecialidad nombre de la especialidad filtrada, o null si son todas
*/
public record Metricas(LocalDate desde, LocalDate hasta, Long idMedico, Short idEspecialidad,
        String filtroMedico, String filtroEspecialidad,
        long cubiertas, long expiradas, Integer recuperadosPct,
        long realizadas, long noAsistio, Integer noAsistenciaPct, List<NoAsistenciaMedico> porMedico,
        long indicadasAgendadas, long indicadasCerradas, Integer indicadasPct,
        long citasConEventos, double reprogramacionesPromedio, long reprogramacionesMax, long[] citasPorReprogramaciones,
        long minutosRecuperados, long abiertasAhora, List<NoAsistenciaGrupo> porEspecialidad,
        List<NoAsistenciaGrupo> porHora,
        long horariosRecuperados, long horariosSoltados, long minutosSoltados,
        ResumenCitas resumen, List<DetalleMedico> detalleMedicos) {

    /** «todos los médicos · todas las especialidades», o lo filtrado. */
    public String descripcionFiltro() {
        return (filtroMedico != null ? filtroMedico : "todos los médicos") + " · "
            + (filtroEspecialidad != null ? filtroEspecialidad : "todas las especialidades");
    }

    /** Tasa de no asistencia de un médico en el rango. */
    public record NoAsistenciaMedico(String medico, String especialidad, long realizadas, long noAsistio, Integer pct) {
    }

    /** Tasa de no asistencia de un grupo (una especialidad o una hora de inicio, por ejemplo «09:00»). */
    public record NoAsistenciaGrupo(String grupo, long realizadas, long noAsistio, Integer pct) {
    }

    /**
    Citas del rango por estado. {@code porAtender}: programadas o confirmadas; {@code enAtencion}: el paciente llegó y
    la atención no se ha cerrado; {@code reprogramadas}: citas que se movieron al menos una vez (siguen contándose en
    su estado actual).
    */
    public record ResumenCitas(long total, long porAtender, long enAtencion, long realizadas, long noAsistio,
            long canceladasPaciente, long canceladasClinica, long reprogramadas) {
    }

    /** El resumen de citas de un médico y lo que recuperó de sus horarios soltados. */
    public record DetalleMedico(String medico, String especialidades, ResumenCitas resumen, long minutosRecuperados,
            long horariosRecuperados) {
    }
}
