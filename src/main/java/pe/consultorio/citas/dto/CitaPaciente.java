package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/** Una cita en «Mis citas» del paciente. */
public record CitaPaciente(Long idCita, LocalDateTime inicio, LocalDateTime fin, String paciente, String medico,
        String especialidad, String tipo, String modalidad, String estado, boolean cancelable) {
}
