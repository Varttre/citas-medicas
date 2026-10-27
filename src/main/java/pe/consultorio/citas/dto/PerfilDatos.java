package pe.consultorio.citas.dto;

import java.time.LocalDate;

/** Datos que el paciente ve en «Mi perfil». Solo el teléfono, el correo y la contraseña son editables. */
public record PerfilDatos(String correo, String documento, String nombres, String apellidos, LocalDate fechaNacimiento,
        String telefono) {
}
