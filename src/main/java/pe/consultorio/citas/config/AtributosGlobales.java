package pe.consultorio.citas.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.consultorio.citas.service.ConfiguracionService;

/** Datos que todas las vistas necesitan: nombre del consultorio, usuario de la barra lateral y WhatsApp. */
@ControllerAdvice
public class AtributosGlobales {

    private final ConfiguracionService configuracion;
    private final String nombreConsultorio;

    public AtributosGlobales(ConfiguracionService configuracion,
            @Value("${app.consultorio.nombre:Consultorio San Isidro}") String nombreConsultorio) {
        this.configuracion = configuracion;
        this.nombreConsultorio = nombreConsultorio;
    }

    @ModelAttribute("consultorio")
    public String consultorio() {
        return nombreConsultorio;
    }

    /** El usuario de la sesión, o null si no hay sesión o no es una cuenta del sistema. */
    @ModelAttribute("usuarioSesion")
    public UsuarioSesion usuarioSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UsuarioSesion u ? u : null;
    }

    /** Texto del rol bajo el nombre, en la barra lateral. */
    @ModelAttribute("rolMostrado")
    public String rolMostrado() {
        UsuarioSesion u = usuarioSesion();
        if (u == null) {
            return "";
        }
        return switch (u.getRol()) {
            case PACIENTE -> "Paciente";
            case MEDICO -> "Médico";
            case RECEPCION -> "Recepción";
            case ADMINISTRADOR -> "Administrador";
            case SUPERADMIN -> "Superadministrador";
        };
    }

    /**
    Genera el token CSRF antes de dibujar la vista. Si se pidiera recién al llegar al formulario de «Cerrar sesión»
    (al final de la barra), en una página larga la respuesta ya estaría enviada y no se podría crear la sesión.
    */
    @ModelAttribute
    public void prepararCsrf(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }

    /** El enlace de WhatsApp solo aparece si el administrador configuró un número. */
    @ModelAttribute("whatsappDisponible")
    public boolean whatsappDisponible() {
        try {
            return configuracion.parametros().getWhatsappNumero() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
