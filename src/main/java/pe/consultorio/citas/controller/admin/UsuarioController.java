package pe.consultorio.citas.controller.admin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
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
import pe.consultorio.citas.controller.FormularioHorario;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.UsuarioService;

/** Usuarios del personal: lista con filtros, alta de médicos y de personal, bloqueo, desbloqueo y baja. */
@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    /** Horario con el que se abre el alta: lunes a viernes de 8:00 a 13:00. */
    private static final Map<String, String> HORARIO_SUGERIDO = FormularioHorario.valores(
        java.util.stream.IntStream.rangeClosed(1, 5)
            .mapToObj(d -> new FranjaDatos(d, LocalTime.of(8, 0), LocalTime.of(13, 0))).toList());

    private final UsuarioService usuarioService;
    private final ConfiguracionService configuracion;
    private final pe.consultorio.citas.service.RecuperacionService recuperacion;

    public UsuarioController(UsuarioService usuarioService, ConfiguracionService configuracion,
            pe.consultorio.citas.service.RecuperacionService recuperacion) {
        this.usuarioService = usuarioService;
        this.configuracion = configuracion;
        this.recuperacion = recuperacion;
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
        // Tras un error del alta, el formulario vuelve abierto con lo escrito (llega como atributo flash).
        @SuppressWarnings("unchecked")
        Map<String, String> enviado = (Map<String, String>) model.getAttribute("altaMedico");
        model.addAttribute("altaMedico", enviado != null ? enviado : Map.of());
        model.addAttribute("grillaAlta", enviado != null ? FormularioHorario.soloGrilla(enviado) : HORARIO_SUGERIDO);
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

    /**
    Las duraciones llegan como campos «dur_{idTipo}». El horario semanal, como «dia_{d}» (casilla del día, 1 = lunes)
    con hasta dos franjas «ini_{d}_{n}» / «fin_{d}_{n}»; una franja con las dos horas vacías se ignora.
    */
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
            List<FranjaDatos> franjas = FormularioHorario.leer(campos);
            if (franjas.isEmpty()) {
                throw new ReglaNegocioException("Marca al menos un día de atención con su horario.", "horario");
            }
            String desde = campos.get("horarioDesde");
            usuarioService.crearMedico(correo, contrasena, nombres, apellidos, idEspecialidad, duraciones,
                desde == null || desde.isBlank() ? null : LocalDate.parse(desde), franjas, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Médico creado con su horario. Ya se le pueden agendar citas.");
        } catch (ReglaNegocioException e) {
            // Vuelve con el formulario abierto, lo escrito (menos la contraseña) y el campo a corregir marcado.
            Map<String, String> enviado = new HashMap<>(campos);
            enviado.remove("contrasena");
            enviado.remove("_csrf");
            flash.addFlashAttribute("altaMedico", enviado);
            flash.addFlashAttribute("campoError", e.getCampo() != null ? e.getCampo() : "general");
            flash.addFlashAttribute("errorAlta", e.getMessage());
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

    /** Enlace de un solo uso para que la persona cree una contraseña nueva (sin correo). */
    @PostMapping("/{id}/enlace-recuperacion")
    public String enlaceRecuperacion(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            String codigo = recuperacion.generarParaCuenta(id, usuario.getIdCuenta());
            flash.addFlashAttribute("enlace", org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/recuperar/cambiar").queryParam("codigo", codigo).toUriString());
        } catch (pe.consultorio.citas.exception.ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
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
