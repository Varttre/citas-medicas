package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.repository.CuentaRepository;

/** Accesos de una cuenta: intentos fallidos, bloqueo temporal y cierre de sus sesiones abiertas. */
@Service
public class CuentaService {

    private final CuentaRepository cuentas;
    private final ConfiguracionService configuracion;
    private final AuditoriaService auditoria;
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sesiones;
    private final Clock reloj;

    public CuentaService(CuentaRepository cuentas, ConfiguracionService configuracion, AuditoriaService auditoria,
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sesiones, Clock reloj) {
        this.cuentas = cuentas;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    /** Login correcto: reinicia el contador y deja constancia. */
    @Transactional
    public void registrarAccesoCorrecto(Long idCuenta) {
        cuentas.findById(idCuenta).ifPresent(c -> {
            c.setIntentosFallidos((short) 0);
            c.setBloqueadoHasta(null);
        });
        auditoria.registrar(idCuenta, AccionAuditoria.LOGIN_OK, "CUENTA", String.valueOf(idCuenta), null, null);
    }

    /**
    Login fallido. Si la cuenta existe suma un intento; al llegar al máximo la bloquea unos minutos (parámetros del
    consultorio) y reinicia el contador. Siempre queda en la auditoría, sin cuenta si el correo no existe.
    */
    @Transactional
    public void registrarIntentoFallido(String correo) {
        Cuenta c = cuentas.findByCorreo(UsuarioDetallesService.normalizar(correo)).orElse(null);
        if (c == null) {
            auditoria.registrar(null, AccionAuditoria.LOGIN_FALLIDO, "CUENTA", null, "correo: " + correo, null);
            return;
        }
        ParametroConsultorio p = configuracion.parametros();
        int intentos = c.getIntentosFallidos() + 1;
        if (intentos >= p.getMaxIntentosLogin()) {
            c.setBloqueadoHasta(LocalDateTime.now(reloj).plusMinutes(p.getMinutosBloqueoLogin()));
            c.setIntentosFallidos((short) 0);
            auditoria.registrar(c.getIdCuenta(), AccionAuditoria.CUENTA_BLOQUEADA, "CUENTA",
                String.valueOf(c.getIdCuenta()), "bloqueada " + p.getMinutosBloqueoLogin() + " min", null);
        } else {
            c.setIntentosFallidos((short) intentos);
        }
        auditoria.registrar(c.getIdCuenta(), AccionAuditoria.LOGIN_FALLIDO, "CUENTA",
            String.valueOf(c.getIdCuenta()), "intento " + intentos, null);
    }

    /** Cierra todas las sesiones abiertas de ese usuario (al bloquearlo, darlo de baja o cambiar su contraseña). */
    public void cerrarSesiones(String correo) {
        FindByIndexNameSessionRepository<? extends Session> repo = sesiones.getIfAvailable();
        if (repo == null) {
            return;
        }
        repo.findByPrincipalName(correo).keySet().forEach(repo::deleteById);
    }
}
