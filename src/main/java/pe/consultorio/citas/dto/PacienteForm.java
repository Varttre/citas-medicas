package pe.consultorio.citas.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.consultorio.citas.entity.TipoDocumento;

/** Datos del formulario de registro o edición de un paciente en recepción. */
public class PacienteForm {

    @NotNull(message = "Elige el tipo de documento.")
    private TipoDocumento tipoDocumento = TipoDocumento.DNI;

    @NotBlank(message = "Ingresa el número de documento.")
    @Size(max = 20)
    private String nroDocumento;

    @NotBlank(message = "Ingresa los nombres.")
    @Size(max = 80)
    private String nombres;

    @NotBlank(message = "Ingresa los apellidos.")
    @Size(max = 80)
    private String apellidos;

    @NotNull(message = "Ingresa la fecha de nacimiento.")
    @Past(message = "La fecha de nacimiento debe ser pasada.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimiento;

    @Pattern(regexp = "^$|^[0-9 +]{6,20}$", message = "El teléfono solo admite números.")
    private String telefono;

    @Email(message = "El correo no es válido.")
    @Size(max = 120)
    private String correo;

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNroDocumento() {
        return nroDocumento;
    }

    public void setNroDocumento(String nroDocumento) {
        this.nroDocumento = nroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
}
