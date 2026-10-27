package pe.consultorio.citas.controller.medico;

import java.time.Clock;
import java.time.LocalDate;

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
import pe.consultorio.citas.service.AgendaService;
import pe.consultorio.citas.service.CitaEstadoService;

/**
Agenda del día del médico. Toda consulta usa el médico de la sesión: no hay selector de médico, y una cita de otro
médico se trata como inexistente (lo comprueba CitaEstadoService).
*/
@Controller
@RequestMapping("/medico")
public class AgendaMedicoController {

    private final AgendaService agenda;
    private final CitaEstadoService estados;
    private final Clock reloj;

    public AgendaMedicoController(AgendaService agenda, CitaEstadoService estados, Clock reloj) {
        this.agenda = agenda;
        this.estados = estados;
        this.reloj = reloj;
    }

    @GetMapping("/agenda")
    public String agenda(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, Model model) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(reloj);
        model.addAttribute("fecha", dia);
        model.addAttribute("citas", usuario == null || usuario.getIdMedico() == null ? java.util.List.of()
            : agenda.delMedico(usuario.getIdMedico(), dia));
        return "medico/agenda";
    }

    @PostMapping("/citas/{id}/{accion:iniciar|finalizar|no-asistio|cancelar}")
    public String accion(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @PathVariable String accion, @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String fecha, RedirectAttributes flash) {
        try {
            switch (accion) {
                case "iniciar" -> estados.iniciarAtencion(id, usuario.getIdCuenta());
                case "finalizar" -> estados.finalizarAtencion(id, usuario.getIdCuenta());
                case "no-asistio" -> estados.marcarNoAsistio(id, usuario.getIdCuenta());
                default -> estados.cancelarPorClinica(id, usuario.getIdCuenta(), motivo);
            }
            flash.addFlashAttribute("exito", "Cita actualizada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/medico/agenda" + (fecha == null || fecha.isBlank() ? "" : "?fecha=" + fecha);
    }
}
