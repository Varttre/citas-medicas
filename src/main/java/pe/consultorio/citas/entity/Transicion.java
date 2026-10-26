package pe.consultorio.citas.entity;

/**
Cambio de estado que tiene un plazo configurable. RETIRO: el paciente llegó y se fue sin ser
atendido (ASISTIO a NO_ASISTIO). Sus valores son los mismos que acepta la base de datos.
*/
public enum Transicion {
    NO_ASISTIO, RETIRO, CANCELADA_POR_PACIENTE, CANCELADA_POR_CLINICA,
    CONFIRMADA, ASISTIO, REALIZADA
}
