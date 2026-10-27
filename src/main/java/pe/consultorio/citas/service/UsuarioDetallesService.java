package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoRepository;

/** Carga la cuenta por correo para Spring Security y arma el usuario de la sesión. */
@Service
public class UsuarioDetallesService implements UserDetailsService {

    private final CuentaRepository cuentas;
    private final MedicoRepository medicos;
    private final Clock reloj;

    public UsuarioDetallesService(CuentaRepository cuentas, MedicoRepository medicos, Clock reloj) {
        this.cuentas = cuentas;
        this.medicos = medicos;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioSesion loadUserByUsername(String correo) {
        Cuenta c = cuentas.findByCorreo(normalizar(correo))
            .orElseThrow(() -> new UsernameNotFoundException("Cuenta inexistente"));
        return armar(c);
    }

    /** Usuario de sesión a partir de la cuenta (lo usa también «entrar como» del superadministrador). */
    @Transactional(readOnly = true)
    public UsuarioSesion armar(Cuenta c) {
        NombreRol rol = c.getRol().getNombre();
        Long idMedico = null;
        Long idPaciente = null;
        String nombre = c.getNombreVisible();
        if (rol == NombreRol.MEDICO) {
            Medico m = medicos.findByCuentaIdCuenta(c.getIdCuenta()).orElse(null);
            if (m != null) {
                idMedico = m.getIdMedico();
                nombre = m.getNombres() + " " + m.getApellidos();
            }
        } else if (rol == NombreRol.PACIENTE && c.getPacientePropio() != null) {
            idPaciente = c.getPacientePropio().getIdPaciente();
            nombre = c.getPacientePropio().getNombres();
        }
        return new UsuarioSesion(c.getIdCuenta(), c.getCorreo(), c.getPasswordHash(), rol, idMedico, idPaciente,
            nombre != null ? nombre : c.getCorreo(), c.getEstado(), c.getBloqueadoHasta(), LocalDateTime.now(reloj));
    }

    /** Correo en minúsculas y sin espacios, como se guarda. */
    public static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase();
    }
}
