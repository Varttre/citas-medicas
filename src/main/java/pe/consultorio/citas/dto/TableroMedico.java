package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.util.List;

/**
El inicio del médico, solo con sus citas: contadores de hoy, la próxima cita, su puntualidad y las alertas.
{@code retrasoMin}: minutos de diferencia de su última cita iniciada hoy (positivo = atrasado; null si aún no inicia
ninguna).
*/
public record TableroMedico(long citasHoy, long confirmadas, long atendidas, long pendientes, CitaAgenda proxima,
        Long retrasoMin, List<IndicacionPendiente> indicaciones, List<String> horariosRechazados) {

    /** Una indicación de seguimiento que dio el médico y aún no se agenda. */
    public record IndicacionPendiente(String paciente, String tipo, LocalDate vigenteHasta) {
    }
}
