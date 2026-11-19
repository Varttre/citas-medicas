package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Una fila de cualquiera de las colas de llamadas de recepción (confirmaciones, cupos liberados, citas pendientes).
{@code id} es la cita, el horario liberado o la indicación, según la cola. {@code estado} es el resultado de la
última llamada («Sin llamar» si no hubo).
*/
public record FilaCola(Long id, String paciente, String telefono, boolean telefonoInvalido, String medico,
        LocalDateTime fechaHora, String detalle, long intentos, String estado, boolean urgente) {
}
