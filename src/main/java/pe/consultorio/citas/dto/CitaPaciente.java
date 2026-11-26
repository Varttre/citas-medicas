package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Una cita en «Mis citas» del paciente. {@code tercero}: la cita es de otra persona que la cuenta agendó.
{@code preferencia}: disponibilidad inmediata vigente (null si no tiene). {@code cancelarHasta}: hasta cuándo puede
cancelarla el paciente (null si no hay límite).
*/
public record CitaPaciente(Long idCita, LocalDateTime inicio, LocalDateTime fin, String paciente, String medico,
        String especialidad, String tipo, String modalidad, String estado, boolean cancelable, boolean tercero,
        String indicaciones, String indicacionesAdmin, PreferenciaDatos preferencia, LocalDateTime cancelarHasta,
        boolean elegibleAdelanto, long diasHastaCita) {
}
