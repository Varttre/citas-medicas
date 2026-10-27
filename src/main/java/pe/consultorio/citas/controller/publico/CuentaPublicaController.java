package pe.consultorio.citas.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
Pantallas públicas de la cuenta: registro y recuperación de contraseña. Por ahora muestran el diseño del prototipo;
el registro real llega con C2 y la recuperación con C3.
*/
@Controller
public class CuentaPublicaController {

    @GetMapping("/registro")
    public String registro() {
        return "publico/registro";
    }

    @GetMapping("/recuperar")
    public String recuperar() {
        return "publico/recuperar";
    }

    @GetMapping("/recuperar/cambiar")
    public String cambiarContrasena() {
        return "publico/cambiar-contrasena";
    }
}
