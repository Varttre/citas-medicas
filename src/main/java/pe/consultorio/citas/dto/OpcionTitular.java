package pe.consultorio.citas.dto;

/**
Una cuenta web que puede quedar como titular (contacto) de la cita que agenda recepción. {@code propia}: es la cuenta
propia del paciente; si no, es la de alguien que agenda por él. {@code telefono}: al que se llamará por esa cita.
*/
public record OpcionTitular(Long idCuenta, String correo, boolean propia, String telefono) {
}
