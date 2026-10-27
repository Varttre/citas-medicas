package pe.consultorio.citas.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.PerfilDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;

/** «Mi perfil» del paciente y la cancelación de sus propias citas desde el portal. */
@Service
public class PerfilService {

    private final CuentaRepository cuentas;
    private final CitaRepository citas;
    private final CitaEstadoService estados;
    private final CuentaService cuentaService;
    private final PasswordEncoder encoder;

    public PerfilService(CuentaRepository cuentas, CitaRepository citas, CitaEstadoService estados,
            CuentaService cuentaService, PasswordEncoder encoder) {
        this.cuentas = cuentas;
        this.citas = citas;
        this.estados = estados;
        this.cuentaService = cuentaService;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PerfilDatos ver(Long idCuenta) {
        Cuenta c = cuenta(idCuenta);
        Paciente p = c.getPacientePropio();
        return new PerfilDatos(c.getCorreo(), p.getTipoDocumento() + " " + p.getNroDocumento(), p.getNombres(),
            p.getApellidos(), p.getFechaNacimiento(), p.getTelefono());
    }

    /**
    Cambia el teléfono del paciente propio y el correo de la cuenta (único).
    @return true si cambió el correo: la sesión abierta queda con el correo anterior y hay que volver a entrar
    */
    @Transactional
    public boolean cambiarDatos(Long idCuenta, String telefono, String correo) {
        Cuenta c = cuenta(idCuenta);
        if (telefono == null || !telefono.trim().matches("[0-9 +]{6,20}")) {
            throw new ReglaNegocioException("Ingresa un teléfono válido.");
        }
        c.getPacientePropio().setTelefono(telefono.trim());
        String nuevo = UsuarioDetallesService.normalizar(correo);
        if (nuevo.equals(c.getCorreo())) {
            return false;
        }
        if (!nuevo.contains("@")) {
            throw new ReglaNegocioException("Ingresa un correo válido.");
        }
        if (cuentas.findByCorreo(nuevo).isPresent()) {
            throw new ReglaNegocioException("Ese correo ya tiene una cuenta.");
        }
        String anterior = c.getCorreo();
        c.setCorreo(nuevo);
        cuentaService.cerrarSesiones(anterior);
        return true;
    }

    /** Cambia la contraseña comprobando la actual; después se cierran todas las sesiones de la cuenta. */
    @Transactional
    public void cambiarContrasena(Long idCuenta, String actual, String nueva, String confirmacion) {
        Cuenta c = cuenta(idCuenta);
        if (actual == null || !encoder.matches(actual, c.getPasswordHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < 8 || nueva.length() > 72) {
            throw new ReglaNegocioException("La nueva contraseña debe tener entre 8 y 72 caracteres.");
        }
        if (!nueva.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
        c.setPasswordHash(encoder.encode(nueva));
        cuentaService.cerrarSesiones(c.getCorreo());
    }

    /** Cancela una cita del paciente: debe ser de su paciente propio o haberla agendado su cuenta. */
    @Transactional
    public void cancelarCita(Long idCuenta, Long idCita) {
        Cuenta c = cuenta(idCuenta);
        Cita cita = citas.findById(idCita)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
        boolean propia = c.getPacientePropio() != null
            && cita.getPaciente().getIdPaciente().equals(c.getPacientePropio().getIdPaciente());
        boolean agendadaPorMi = cita.getCuentaTitular() != null && cita.getCuentaTitular().getIdCuenta().equals(idCuenta);
        if (!propia && !agendadaPorMi) {
            throw new RecursoNoEncontradoException("Cita " + idCita + " de otro paciente");
        }
        estados.cancelarPorPaciente(idCita, idCuenta, null);
    }

    private Cuenta cuenta(Long idCuenta) {
        return cuentas.findById(idCuenta)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta inexistente " + idCuenta));
    }
}
