package pe.consultorio.citas.controller.publico;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.PerfilService;

/**
«Mi perfil» del personal (médico, recepción y administrador): sus datos de cuenta y el cambio de contraseña. El
paciente tiene su propio perfil en el portal, con teléfono y correo.
*/
@Controller
@RequestMapping("/cuenta")
public class MiPerfilController {

    private final PerfilService perfil;

    public MiPerfilController(PerfilService perfil) {
        this.perfil = perfil;
    }

    @GetMapping("/perfil")
    public String ver(@AuthenticationPrincipal UsuarioSesion usuario) {
        return usuario != null && usuario.getIdPacientePropio() != null ? "redirect:/paciente/perfil" : "publico/mi-perfil";
    }

    /** Tras cambiarla se cierran todas las sesiones de la cuenta: se vuelve a entrar con la nueva. */
    @PostMapping("/perfil/contrasena")
    public String cambiarContrasena(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam String actual,
            @RequestParam String nueva, @RequestParam String confirmacion, RedirectAttributes flash) {
        try {
            perfil.cambiarContrasena(usuario.getIdCuenta(), actual, nueva, confirmacion);
            return "redirect:/login?contrasenaCambiada";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/cuenta/perfil";
        }
    }
}
