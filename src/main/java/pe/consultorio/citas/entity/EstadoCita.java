package pe.consultorio.citas.entity;

/**
Estado de una cita. Una cita cancelada deja de ocupar su horario. Sus valores son los mismos que
acepta la base de datos.
*/
public enum EstadoCita {
    PROGRAMADA, CONFIRMADA, ASISTIO, REALIZADA, NO_ASISTIO,
    CANCELADA_POR_PACIENTE, CANCELADA_POR_CLINICA
}
