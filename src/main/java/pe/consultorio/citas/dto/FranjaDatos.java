package pe.consultorio.citas.dto;

import java.time.LocalTime;

/**
Un lapso de atención semanal.

@param diaSemana 1 = lunes … 7 = domingo
@param inicio    hora de inicio, en la rejilla
@param fin       hora de fin, en la rejilla y posterior al inicio
*/
public record FranjaDatos(int diaSemana, LocalTime inicio, LocalTime fin) {
}
