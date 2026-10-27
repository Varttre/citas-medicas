package pe.consultorio.citas.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import pe.consultorio.citas.service.CuentaService;

/** Tras un login fallido: cuenta el intento y vuelve al formulario con el mensaje que corresponde. */
@Component
public class ManejadorLoginFallido implements AuthenticationFailureHandler {

    private final CuentaService cuentaService;

    public ManejadorLoginFallido(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }
        cuentaService.registrarIntentoFallido(request.getParameter("correo"));
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
