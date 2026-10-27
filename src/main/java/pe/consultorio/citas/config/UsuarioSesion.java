package pe.consultorio.citas.config;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.NombreRol;

/**
Usuario de la sesión. Se guarda serializado en la base (Spring Session JDBC), por eso solo lleva tipos simples:
nada de entidades. Los controladores lo reciben con {@code @AuthenticationPrincipal UsuarioSesion usuario}.
*/
public class UsuarioSesion implements UserDetails {

    private final Long idCuenta;
    private final String correo;
    private final String passwordHash;
    private final NombreRol rol;
    private final Long idMedico;
    private final Long idPacientePropio;
    private final String nombreMostrado;
    private final EstadoCuenta estado;
    private final LocalDateTime bloqueadoHasta;
    private final LocalDateTime ahora;

    public UsuarioSesion(Long idCuenta, String correo, String passwordHash, NombreRol rol, Long idMedico,
            Long idPacientePropio, String nombreMostrado, EstadoCuenta estado, LocalDateTime bloqueadoHasta,
            LocalDateTime ahora) {
        this.idCuenta = idCuenta;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.idMedico = idMedico;
        this.idPacientePropio = idPacientePropio;
        this.nombreMostrado = nombreMostrado;
        this.estado = estado;
        this.bloqueadoHasta = bloqueadoHasta;
        this.ahora = ahora;
    }

    public Long getIdCuenta() {
        return idCuenta;
    }

    public NombreRol getRol() {
        return rol;
    }

    /** null si no es médico. */
    public Long getIdMedico() {
        return idMedico;
    }

    /** null si no es paciente. */
    public Long getIdPacientePropio() {
        return idPacientePropio;
    }

    /** Paciente: sus nombres; médico: nombres y apellidos; personal: el nombre visible de la cuenta. */
    public String getNombreMostrado() {
        return nombreMostrado;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isEnabled() {
        return estado != EstadoCuenta.BAJA;
    }

    /** Bloqueada a mano por el administrador, o por intentos fallidos hasta {@code bloqueadoHasta}. */
    @Override
    public boolean isAccountNonLocked() {
        return estado != EstadoCuenta.BLOQUEADA && (bloqueadoHasta == null || bloqueadoHasta.isBefore(ahora));
    }
}
