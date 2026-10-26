package pe.consultorio.citas.service.disponibilidad;

/**
Qué tan bien encaja una cita en un bloque libre según los minutos que sobran.
PERFECTO: no sobra nada. VENDIBLE: sobra lo suficiente para otra cita. BASURA: sobra un hueco inútil.
*/
public enum Calce {
    PERFECTO, VENDIBLE, BASURA
}
