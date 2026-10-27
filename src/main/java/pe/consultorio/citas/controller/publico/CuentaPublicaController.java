package pe.consultorio.citas.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
Pantallas públicas de recuperación de contraseña. Por ahora muestran el diseño del prototipo; la recuperación real
llega con C3. El registro lo atiende RegistroController.
*/
@Controller
public class CuentaPublicaController {

    @GetMapping("/recuperar")
    public String recuperar() {
        return "publico/recuperar";
    }

    @GetMapping("/recuperar/cambiar")
    public String cambiarContrasena() {
        return "publico/cambiar-contrasena";
    }
}
