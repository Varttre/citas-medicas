package pe.consultorio.citas.controller.admin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.UsuarioService;

/** Usuarios del personal: lista con filtros, alta de médicos y de personal, bloqueo, desbloqueo y baja. */
@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final ConfiguracionService configuracion;

    public UsuarioController(UsuarioService usuarioService, ConfiguracionService configuracion) {
        this.usuarioService = usuarioService;
        this.configuracion = configuracion;
    }

    @GetMapping
    public String lista(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam(required = false) String q,
            @RequestParam(required = false) NombreRol rol, Model model) {
        boolean esSuperadmin = usuario != null && usuario.getRol() == NombreRol.SUPERADMIN;
        model.addAttribute("q", q);
        model.addAttribute("rol", rol);
        model.addAttribute("usuarios", usuarioService.listar(q, rol, esSuperadmin));
        model.addAttribute("ahora", usuarioService.ahora());
        model.addAttribute("tipos", configuracion.tiposActivos());
        model.addAttribute("especialidades", configuracion.especialidadesActivas());
        return "admin/usuarios";
    }

    @PostMapping("/personal")
    public String altaPersonal(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam String correo,
            @RequestParam String contrasena, @RequestParam NombreRol rol, @RequestParam String nombreVisible,
            RedirectAttributes flash) {
        try {
            usuarioService.crearPersonal(correo, contrasena, rol, nombreVisible, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Usuario creado.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    /** Las duraciones llegan como campos «dur_{idTipo}». */
    @PostMapping("/medicos")
    public String altaMedico(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam String correo,
            @RequestParam String contrasena, @RequestParam String nombres, @RequestParam String apellidos,
            @RequestParam(required = false) Short idEspecialidad, @RequestParam Map<String, String> campos,
            RedirectAttributes flash) {
        Map<Short, Integer> duraciones = new HashMap<>();
        campos.forEach((k, v) -> {
            if (k.startsWith("dur_") && !v.isBlank()) {
                duraciones.put(Short.valueOf(k.substring(4)), Integer.valueOf(v));
            }
        });
        try {
            usuarioService.crearMedico(correo, contrasena, nombres, apellidos, idEspecialidad, duraciones,
                usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Médico creado. Ya puede definir su horario en «Mi horario».");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/bloquear")
    public String bloquear(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        usuarioService.bloquear(id, "Bloqueo manual del administrador", usuario.getIdCuenta());
        flash.addFlashAttribute("exito", "Cuenta bloqueada y sesiones cerradas.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/desbloquear")
    public String desbloquear(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        usuarioService.desbloquear(id, usuario.getIdCuenta());
        flash.addFlashAttribute("exito", "Cuenta desbloqueada.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/baja")
    public String baja(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        usuarioService.darDeBaja(id, usuario.getIdCuenta());
        flash.addFlashAttribute("exito", "Cuenta dada de baja.");
        return "redirect:/admin/usuarios";
    }
}
