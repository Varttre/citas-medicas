package pe.consultorio.citas.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import pe.consultorio.citas.service.CuentaService;

/** Tras un login correcto: reinicia los intentos y lleva a cada rol a su inicio. */
@Component
public class ManejadorLoginExitoso implements AuthenticationSuccessHandler {

    private final CuentaService cuentaService;

    public ManejadorLoginExitoso(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        UsuarioSesion usuario = (UsuarioSesion) authentication.getPrincipal();
        cuentaService.registrarAccesoCorrecto(usuario.getIdCuenta());
        response.sendRedirect(request.getContextPath() + destino(usuario));
    }

    /** Página de inicio de cada rol. */
    public static String destino(UsuarioSesion usuario) {
        return switch (usuario.getRol()) {
            case PACIENTE -> "/paciente/inicio";
            case MEDICO -> "/medico/inicio";
            case RECEPCION, ADMINISTRADOR, SUPERADMIN -> "/recepcion/inicio";
        };
    }
}
