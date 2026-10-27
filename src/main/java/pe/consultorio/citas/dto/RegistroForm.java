package pe.consultorio.citas.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.consultorio.citas.entity.TipoDocumento;

/** Formulario de registro de una cuenta de paciente. Las longitudes son las de las columnas de cuenta y paciente. */
public class RegistroForm {

    @NotBlank(message = "Ingresa tu correo.")
    @Email(message = "El correo no es válido.")
    @Size(max = 120)
    private String correo;

    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    private String contrasena;

    private String confirmacion;

    @NotNull
    private TipoDocumento tipoDocumento = TipoDocumento.DNI;

    @NotBlank(message = "Ingresa tu número de documento.")
    @Size(max = 20)
    private String nroDocumento;

    @NotBlank(message = "Ingresa tus nombres.")
    @Size(max = 80)
    private String nombres;

    @NotBlank(message = "Ingresa tus apellidos.")
    @Size(max = 80)
    private String apellidos;

    @NotNull(message = "Ingresa tu fecha de nacimiento.")
    @Past(message = "La fecha de nacimiento debe ser pasada.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimiento;

    @NotBlank(message = "Ingresa tu teléfono.")
    @Pattern(regexp = "^[0-9 +]{6,20}$", message = "El teléfono solo admite números.")
    private String telefono;

    @AssertTrue(message = "Debes aceptar los términos y condiciones.")
    private boolean aceptaTerminos;

    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isConfirmacionValida() {
        return contrasena != null && contrasena.equals(confirmacion);
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getConfirmacion() {
        return confirmacion;
    }

    public void setConfirmacion(String confirmacion) {
        this.confirmacion = confirmacion;
    }

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

    public boolean isAceptaTerminos() {
        return aceptaTerminos;
    }

    public void setAceptaTerminos(boolean aceptaTerminos) {
        this.aceptaTerminos = aceptaTerminos;
    }
}
