package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/** Un horario liberado aún sin cubrir, como lo ve recepción en la lista de cupos. */
public record CupoLiberado(Long id, String medico, LocalDateTime inicio, LocalDateTime fin, long minutos,
        long minutosParaEmpezar, int candidatos) {

    /** «Empieza en 1 h 05», «Empieza en 25 min». */
    public String empiezaEn() {
        long m = Math.max(minutosParaEmpezar, 0);
        return m >= 60 ? "Empieza en " + m / 60 + " h " + String.format("%02d", m % 60) : "Empieza en " + m + " min";
    }
}
