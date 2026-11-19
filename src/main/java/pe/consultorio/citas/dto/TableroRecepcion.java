package pe.consultorio.citas.dto;

import java.time.LocalDateTime;
import java.util.List;

/** El inicio de recepción: seis contadores de trabajo pendiente y cómo va cada médico hoy. */
public record TableroRecepcion(LocalDateTime ahora, long porConfirmarHoy, long cupos, long indicadasPendientes,
        long enSala, long ayerSinEstado, long hoySinLlegada, List<MedicoHoy> medicos) {

    /**
    Cómo va un médico hoy. {@code estado}: «Atendiendo», «Retrasado X min», «En horario» o «Terminó por hoy».
    {@code avance}: porcentaje de sus citas de hoy ya cerradas.
    */
    public record MedicoHoy(String medico, String especialidad, String estado, String color, int avance) {
    }
}
