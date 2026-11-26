package pe.consultorio.citas.controller.medico;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import pe.consultorio.citas.controller.FormularioHorario;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.dto.HorarioMedicoDatos;
import pe.consultorio.citas.dto.VersionHorarioDatos;
import pe.consultorio.citas.exception.HorarioConCitasException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.HorarioService;

/**
«Mi horario» del médico: cambiar el horario semanal (desde hoy o una fecha futura), versiones con vigencia,
excepciones y duraciones por tipo de consulta.
Siempre trabaja sobre el médico de la sesión, nunca sobre un id que venga en la URL.
*/
@Controller
@RequestMapping("/medico/horario")
public class HorarioController {

    private final HorarioService horarioService;
    private final Clock reloj;

    public HorarioController(HorarioService horarioService, Clock reloj) {
        this.horarioService = horarioService;
        this.reloj = reloj;
    }

    @GetMapping
    public String ver(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        HorarioMedicoDatos horario = horarioService.verHorario(usuario.getIdMedico());
        model.addAttribute("horario", horario);
        model.addAttribute("dias", new String[] {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"});
        model.addAttribute("hoy", LocalDate.now(reloj));
        // El formulario «Cambiar mi horario» se abre con el horario que rige hoy (o el último programado); tras un
        // error, con lo que se envió.
        @SuppressWarnings("unchecked")
        Map<String, String> enviado = (Map<String, String>) model.getAttribute("horarioEnviado");
        List<FranjaDatos> base = horario.versiones().stream()
            .filter(v -> VersionHorarioDatos.VIGENTE.equals(v.estado())).findFirst()
            .or(() -> horario.versiones().stream().filter(v -> VersionHorarioDatos.PROGRAMADA.equals(v.estado()))
                .reduce((a, b) -> b))
            .map(VersionHorarioDatos::franjas).orElse(List.of());
        model.addAttribute("grillaHorario", enviado != null ? FormularioHorario.soloGrilla(enviado)
            : FormularioHorario.valores(base));
        model.addAttribute("desdeHorario", enviado != null && enviado.get("desde") != null ? enviado.get("desde")
            : LocalDate.now(reloj).toString());
        return "medico/horario";
    }

    /** Cambia el horario semanal desde la fecha elegida (hoy o después). Si deja citas fuera, las lista. */
    @PostMapping("/versiones")
    public String cambiarHorario(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam Map<String, String> campos, RedirectAttributes flash) {
        try {
            String desde = campos.get("desde");
            horarioService.crearVersion(usuario.getIdMedico(),
                desde == null || desde.isBlank() ? null : LocalDate.parse(desde), FormularioHorario.leer(campos),
                usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Horario actualizado desde el "
                + LocalDate.parse(desde).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        } catch (ReglaNegocioException e) {
            Map<String, String> enviado = new HashMap<>(campos);
            enviado.remove("_csrf");
            flash.addFlashAttribute("horarioEnviado", enviado);
            flash.addFlashAttribute("errorHorario", e.getMessage());
            if (e instanceof HorarioConCitasException h) {
                flash.addFlashAttribute("afectadas", h.getCitas());
            }
        } catch (java.time.format.DateTimeParseException e) {
            flash.addFlashAttribute("horarioEnviado", FormularioHorario.soloGrilla(campos));
            flash.addFlashAttribute("errorHorario", "Elige la fecha desde la que rige el horario.");
        }
        return "redirect:/medico/horario";
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
