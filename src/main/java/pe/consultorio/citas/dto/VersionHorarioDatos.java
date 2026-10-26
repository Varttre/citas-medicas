package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.util.List;

/**
Una versión del horario del médico, para mostrar.

@param idVersion    identificador
@param vigenteDesde primer día en que rige
@param vigenteHasta último día en que rige; null = sin fin
@param estado       ANTERIOR, VIGENTE o PROGRAMADA, respecto de hoy
@param franjas      lapsos de atención, ordenados por día y hora
*/
public record VersionHorarioDatos(Long idVersion, LocalDate vigenteDesde, LocalDate vigenteHasta, String estado,
        List<FranjaDatos> franjas) {

    public static final String ANTERIOR = "ANTERIOR";
    public static final String VIGENTE = "VIGENTE";
    public static final String PROGRAMADA = "PROGRAMADA";
}
