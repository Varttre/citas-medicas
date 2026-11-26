package pe.consultorio.citas.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.RecuperacionService;

/**
Recuperación de contraseña asistida: «Olvidé mi contraseña» explica cómo pedir el enlace al consultorio, y el enlace
de un solo uso que genera recepción o el administrador abre el formulario de contraseña nueva. El registro lo atiende
RegistroController.
*/
@Controller
public class CuentaPublicaController {

    private final RecuperacionService recuperacion;

    public CuentaPublicaController(RecuperacionService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @GetMapping("/recuperar")
    public String recuperar() {
        return "publico/recuperar";
    }

    @GetMapping("/recuperar/cambiar")
    public String cambiarContrasena(@RequestParam(required = false) String codigo, Model model) {
        model.addAttribute("codigo", codigo);
        model.addAttribute("correo", recuperacion.correoDelEnlace(codigo).orElse(null));
        return "publico/cambiar-contrasena";
    }

    @PostMapping("/recuperar/cambiar")
    public String guardar(@RequestParam String codigo, @RequestParam String nueva, @RequestParam String confirmacion,
            RedirectAttributes flash) {
        try {
            recuperacion.cambiar(codigo, nueva, confirmacion);
            return "redirect:/login?contrasenaCambiada";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/recuperar/cambiar?codigo=" + codigo;
        }
    }
}
