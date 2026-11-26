package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Un horario liberado que ya se cubrió hoy, como lo ve recepción debajo de la lista de cupos: deja de ofrecerse, pero
queda a la vista quién lo tomó.

@param medico      médico del bloque
@param inicio      inicio de la cita que lo cubrió
@param fin         fin de esa cita
@param paciente    paciente que lo cubrió
@param cubiertoEn  cuándo se cubrió
*/
public record CupoCubierto(String medico, LocalDateTime inicio, LocalDateTime fin, String paciente,
        LocalDateTime cubiertoEn) {
}
