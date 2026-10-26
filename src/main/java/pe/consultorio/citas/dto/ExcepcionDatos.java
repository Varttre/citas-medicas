package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
Una ausencia puntual del médico.

@param idExcepcion identificador
@param fechaInicio primer día
@param fechaFin    último día
@param horaInicio  null = día completo
@param horaFin     null = día completo
@param motivo      motivo de la ausencia
*/
public record ExcepcionDatos(Long idExcepcion, LocalDate fechaInicio, LocalDate fechaFin, LocalTime horaInicio,
        LocalTime horaFin, String motivo) {

    public boolean diaCompleto() {
        return horaInicio == null;
    }
}
