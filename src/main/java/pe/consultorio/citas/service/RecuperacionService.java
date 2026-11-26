package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.TokenRecuperacion;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.TokenRecuperacionRepository;

/**
Recuperación de contraseña asistida por el personal, sin correo: recepción genera un enlace de un solo uso para la
cuenta de un paciente y el administrador para cualquier cuenta. El enlace vale 24 horas, sirve una sola vez y solo
permite poner una contraseña nueva (no inicia sesión). Generar uno nuevo anula los anteriores de esa cuenta.
*/
@Service
public class RecuperacionService {

    static final String AUDIENCIA = "RECUPERAR_CONTRASENA";
    static final int HORAS_VIGENCIA = 24;

    private final TokenRecuperacionRepository tokens;
    private final CuentaRepository cuentas;
    private final CuentaService cuentaService;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    public RecuperacionService(TokenRecuperacionRepository tokens, CuentaRepository cuentas,
            CuentaService cuentaService, AuditoriaService auditoria, PasswordEncoder encoder, Clock reloj) {
        this.tokens = tokens;
        this.cuentas = cuentas;
        this.cuentaService = cuentaService;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    /** Recepción: enlace para la cuenta web propia del paciente. Devuelve el código del enlace. */
    @Transactional
    public String generarParaPaciente(Long idPaciente, Long actor) {
        Cuenta c = cuentas.findAll().stream()
            .filter(x -> x.getPacientePropio() != null && x.getPacientePropio().getIdPaciente().equals(idPaciente))
            .findFirst()
            .orElseThrow(() -> new ReglaNegocioException("Este paciente no tiene cuenta web propia."));
        return generar(c, actor);
    }

    /** Administrador: enlace para cualquier cuenta. Devuelve el código del enlace. */
    @Transactional
    public String generarParaCuenta(Long idCuenta, Long actor) {
        Cuenta c = cuentas.findById(idCuenta).orElseThrow(() -> new ReglaNegocioException("La cuenta no existe."));
        if (c.getRol().getNombre() == NombreRol.SUPERADMIN) {
            throw new ReglaNegocioException("Esa cuenta no se recupera desde aquí.");
        }
        return generar(c, actor);
    }

    /** El correo de la cuenta del enlace, enmascarado, si el enlace sigue sirviendo. */
    @Transactional(readOnly = true)
    public Optional<String> correoDelEnlace(String codigo) {
        return vigente(codigo).map(t -> enmascarar(t.getCuenta().getCorreo()));
    }

    /** Pone la contraseña nueva, gasta el enlace, desbloquea la cuenta y cierra sus sesiones abiertas. */
    @Transactional
    public void cambiar(String codigo, String nueva, String confirmacion) {
        TokenRecuperacion t = vigente(codigo)
            .orElseThrow(() -> new ReglaNegocioException("El enlace ya no es válido: pide uno nuevo en recepción."));
        if (nueva == null || nueva.length() < 8 || nueva.length() > 72) {
            throw new ReglaNegocioException("La nueva contraseña debe tener entre 8 y 72 caracteres.");
        }
        if (!nueva.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
        Cuenta c = t.getCuenta();
        c.setPasswordHash(encoder.encode(nueva));
        c.setIntentosFallidos((short) 0);
        c.setBloqueadoHasta(null);
        t.setConsumidoEn(LocalDateTime.now(reloj));
        cuentaService.cerrarSesiones(c.getCorreo());
        auditoria.registrar(c.getIdCuenta(), AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(c.getIdCuenta()),
            "contraseña restablecida con enlace", null);
    }

    private String generar(Cuenta c, Long actor) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        tokens.findAll().stream()
            .filter(t -> t.getCuenta().getIdCuenta().equals(c.getIdCuenta()) && t.getConsumidoEn() == null && !t.isRevocado())
            .forEach(t -> t.setRevocado(true));
        TokenRecuperacion t = new TokenRecuperacion();
        t.setJti(UUID.randomUUID().toString());
        t.setCuenta(c);
        t.setAud(AUDIENCIA);
        t.setEmitidoEn(ahora);
        t.setExpiraEn(ahora.plusHours(HORAS_VIGENCIA));
        t.setRevocado(false);
        tokens.save(t);
        auditoria.registrar(actor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(c.getIdCuenta()),
            "enlace de recuperación generado", null);
        return t.getJti();
    }

    private Optional<TokenRecuperacion> vigente(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return tokens.findById(codigo.trim())
            .filter(t -> AUDIENCIA.equals(t.getAud()) && !t.isRevocado() && t.getConsumidoEn() == null
                && t.getExpiraEn().isAfter(ahora));
    }

    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return correo;
        }
        return correo.charAt(0) + "•••••" + correo.substring(arroba);
    }
}
