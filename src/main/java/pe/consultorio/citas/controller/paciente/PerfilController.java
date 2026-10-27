package pe.consultorio.citas.controller.paciente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.PerfilService;

/** «Mi perfil» del paciente (teléfono, correo y contraseña) y cancelación de sus citas desde «Mis citas». */
@Controller
@RequestMapping("/paciente")
public class PerfilController {

    private final PerfilService perfil;

    public PerfilController(PerfilService perfil) {
        this.perfil = perfil;
    }

    @GetMapping("/perfil")
    public String ver(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("perfil", perfil.ver(usuario.getIdCuenta()));
        return "paciente/perfil";
    }

    @PostMapping("/perfil/datos")
    public String datos(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam String telefono,
            @RequestParam String correo, HttpServletRequest request, HttpServletResponse response,
            RedirectAttributes flash) {
        try {
            if (perfil.cambiarDatos(usuario.getIdCuenta(), telefono, correo)) {
                salir(request, response);
                return "redirect:/login?correoCambiado";
            }
            flash.addFlashAttribute("exito", "Datos actualizados.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/paciente/perfil";
    }

    @PostMapping("/perfil/contrasena")
    public String contrasena(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam String actual,
            @RequestParam String nueva, @RequestParam String confirmacion, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes flash) {
        try {
            perfil.cambiarContrasena(usuario.getIdCuenta(), actual, nueva, confirmacion);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/paciente/perfil";
        }
        salir(request, response);
        return "redirect:/login?contrasenaCambiada";
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            perfil.cancelarCita(usuario.getIdCuenta(), id);
            flash.addFlashAttribute("exito", "Cita cancelada. El horario ya está disponible para otros pacientes.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/paciente/citas";
    }

    private static void salir(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
}
