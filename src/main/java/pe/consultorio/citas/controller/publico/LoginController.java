package pe.consultorio.citas.controller.publico;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import pe.consultorio.citas.config.ManejadorLoginExitoso;
import pe.consultorio.citas.config.UsuarioSesion;

/** Formulario de inicio de sesión. El POST /login lo atiende Spring Security. */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal UsuarioSesion usuario) {
        if (usuario != null) {
            return "redirect:" + ManejadorLoginExitoso.destino(usuario);
        }
        return "publico/login";
    }
}
