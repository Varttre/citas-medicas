package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Un horario liberado aún sin cubrir, como lo ve recepción en la lista de cupos.

@param inicio          inicio del bloque libre que se ofrece (el horario soltado unido al tiempo libre de alrededor)
@param fin             fin de ese bloque
@param liberadoInicio  inicio del horario que se soltó y originó el bloque (nulo si no se puede reconstruir)
@param liberadoFin     fin de ese horario
@param origen          qué lo liberó, p. ej. «Canceló Carmen Ramírez»
@param restoDe         si es lo que sobró de un cupo ya cubierto, quién lo cubrió y a qué hora (nulo si no)
*/
public record CupoLiberado(Long id, String medico, LocalDateTime inicio, LocalDateTime fin, long minutos,
        long minutosParaEmpezar, int candidatos, LocalDateTime liberadoInicio, LocalDateTime liberadoFin,
        String origen, String restoDe) {

    /** «Empieza en 1 h 05», «Empieza en 25 min». */
    public String empiezaEn() {
        long m = Math.max(minutosParaEmpezar, 0);
        return m >= 60 ? "Empieza en " + m / 60 + " h " + String.format("%02d", m % 60) : "Empieza en " + m + " min";
    }

    /** El bloque ofrecido es más amplio que el horario soltado (se unió con tiempo libre de alrededor). */
    public boolean bloqueAmpliado() {
        return liberadoInicio != null && (inicio.isBefore(liberadoInicio) || fin.isAfter(liberadoFin));
    }
}
