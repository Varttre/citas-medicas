package pe.consultorio.citas.controller.medico;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
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
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.HorarioService;

/**
«Mi horario» del médico: versiones con vigencia, excepciones y duraciones por tipo de consulta.
Siempre trabaja sobre el médico de la sesión, nunca sobre un id que venga en la URL.
*/
@Controller
@RequestMapping("/medico/horario")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @GetMapping
    public String ver(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("horario", horarioService.verHorario(usuario.getIdMedico()));
        model.addAttribute("dias", new String[] {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"});
        return "medico/horario";
    }

    @PostMapping("/excepciones")
    public String crearExcepcion(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
            @RequestParam String motivo, RedirectAttributes flash) {
        try {
            horarioService.crearExcepcion(usuario.getIdMedico(), desde, hasta, horaInicio, horaFin, motivo,
                usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Excepción registrada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/medico/horario#excepciones";
    }

    @PostMapping("/excepciones/{id}/quitar")
    public String quitarExcepcion(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            horarioService.eliminarExcepcion(usuario.getIdMedico(), id);
            flash.addFlashAttribute("exito", "Excepción quitada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/medico/horario";
    }

    @PostMapping("/duraciones/{idTipo}")
    public String cambiarDuracion(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Short idTipo,
            @RequestParam int minutos, RedirectAttributes flash) {
        try {
            horarioService.cambiarDuracion(usuario.getIdMedico(), idTipo, minutos, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Duración actualizada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/medico/horario";
    }
}
