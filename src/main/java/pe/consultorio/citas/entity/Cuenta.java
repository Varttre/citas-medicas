package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Credenciales y estado de acceso. Tabla cuenta. */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuenta", updatable = false)
    private Long idCuenta;

    /** Usuario de inicio de sesión. */
    @Column(name = "correo", nullable = false, length = 120)
    private String correo;

    /**
    Nombre del personal de recepción y administración, para mostrar quién llamó o quién hizo un
    cambio. Lo exige el servicio en esos roles; NULL en pacientes (su nombre sale del paciente
    propio) y médicos (sale de MEDICO).
    */
    @Column(name = "nombre_visible", length = 120)
    private String nombreVisible;

    /** Hash (bcrypt o Argon2), nunca la contraseña. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Rol de la cuenta. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol")
    private Rol rol;

    /**
    Paciente del titular, creado al registrarse. Obligatorio en cuentas con rol PACIENTE (lo valida
    el servicio: un CHECK no puede leer el rol). NULL en cuentas de personal. Su teléfono es el
    contacto de la cuenta.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente_propio")
    private Paciente pacientePropio;

    /**
    ACTIVA, BLOQUEADA (bloqueo manual del administrador, sin fecha de fin) o BAJA. El bloqueo
    temporal por intentos fallidos no usa este campo: es bloqueado_hasta.
    */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoCuenta estado;

    /** Se reinicia al iniciar sesión correctamente. */
    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    /** Fecha de alta. */
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public Long getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(Long idCuenta) {
        this.idCuenta = idCuenta;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public void setNombreVisible(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Paciente getPacientePropio() {
        return pacientePropio;
    }

    public void setPacientePropio(Paciente pacientePropio) {
        this.pacientePropio = pacientePropio;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuenta estado) {
        this.estado = estado;
    }

    public Short getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(Short intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
