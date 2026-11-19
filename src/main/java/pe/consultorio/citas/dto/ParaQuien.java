package pe.consultorio.citas.dto;

import java.io.Serializable;

/**
Para quién es la cita que se está agendando (paso 1 de Nueva cita), guardado en la sesión. {@code aviso}: mensaje
para la cuenta cuando agenda para otra persona (por ejemplo, que el teléfono no se actualizó); puede ser null.
*/
public record ParaQuien(Long idPaciente, String nombre, boolean tercero, String aviso) implements Serializable {
}
