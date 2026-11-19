package pe.consultorio.citas.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Reprogramación en curso, guardada en la sesión entre el paso 1 (horario) y el paso 2 (confirmar). */
public record ReprogramarSesion(Long idCita, LocalDateTime nuevoInicio) implements Serializable {
}
