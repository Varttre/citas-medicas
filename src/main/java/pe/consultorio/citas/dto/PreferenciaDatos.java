package pe.consultorio.citas.dto;

import java.io.Serializable;

import pe.consultorio.citas.entity.AlcancePreferencia;

/**
Condiciones con las que un paciente acepta que lo llamen para adelantar su cita.

@param alcance       mismo médico o cualquiera de la especialidad
@param minLlegadaMin minutos que necesita para llegar desde que lo llaman
@param diasPrevios   cuántos días antes de su cita acepta que se la adelanten
*/
public record PreferenciaDatos(AlcancePreferencia alcance, int minLlegadaMin, int diasPrevios) implements Serializable {
}
