package pe.consultorio.citas.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
Cita que impide un cambio de horario, con lo necesario para listarla.

@param idCita   identificador
@param inicio   inicio de la cita
@param fin      fin de la cita
@param paciente nombres y apellidos del paciente
*/
public record CitaAfectada(Long idCita, LocalDateTime inicio, LocalDateTime fin, String paciente)
        implements Serializable {
}
