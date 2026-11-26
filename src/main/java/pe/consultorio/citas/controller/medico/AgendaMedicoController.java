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
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.dto.IndicacionComando;

/**
Agenda del día del médico. Toda consulta usa el médico de la sesión: no hay selector de médico, y una cita de otro
médico se trata como inexistente (lo comprueba CitaEstadoService).
*/
@Controller
@RequestMapping("/medico")
public class AgendaMedicoController {

    private final AgendaService agenda;
    private final CitaEstadoService estados;
    private final CitaIndicadaService indicadas;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public AgendaMedicoController(AgendaService agenda, CitaEstadoService estados, CitaIndicadaService indicadas,
            ConfiguracionService configuracion, Clock reloj) {
        this.agenda = agenda;
        this.estados = estados;
        this.indicadas = indicadas;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    @GetMapping("/agenda")
    public String agenda(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q, Model model) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(reloj);
        boolean porPaciente = q != null && !q.isBlank();
        LocalDate fin = hasta != null ? hasta : dia;
        model.addAttribute("fecha", dia);
        model.addAttribute("hasta", hasta);
        model.addAttribute("q", porPaciente ? q.trim() : null);
        model.addAttribute("porPaciente", porPaciente);
        model.addAttribute("unDia", !porPaciente && fin.equals(dia));
        // Por día, por rango de días o por paciente; siempre solo las citas de este médico.
        java.util.List<pe.consultorio.citas.dto.CitaAgenda> delDia = java.util.List.of();
        if (usuario != null && usuario.getIdMedico() != null) {
            try {
                delDia = agenda.buscar(porPaciente ? null : dia, porPaciente ? null : fin, usuario.getIdMedico(), null,
                    null, q);
            } catch (pe.consultorio.citas.exception.ReglaNegocioException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("citas", delDia);
        // Las citas indicadas que el paciente aún no agenda, a la vista al atenderlo (de este u otro médico).
        model.addAttribute("pendientesPorCita", indicadas.pendientesPorCita(
            delDia.stream().map(pe.consultorio.citas.dto.CitaAgenda::idCita).toList()));
        model.addAttribute("tipos", configuracion.tiposActivos());
        model.addAttribute("indicadasPorCita", usuario == null || usuario.getIdMedico() == null ? java.util.Map.of()
            : indicadas.pendientesDeMedico(usuario.getIdMedico()).stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getCitaOrigen().getIdCita(),
                    i -> i.getTipoConsulta().getNombre() + " desde el "
                        + i.getFechaRecomendada().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                    (a, b) -> a)));
        return "medico/agenda";
    }

    /** El médico indica la siguiente cita del paciente de una consulta atendida; recepción la ve en pendientes. */
    @PostMapping("/citas/{id}/indicar")
    public String indicar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam Short idTipo, @RequestParam int duracion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaRecomendada,
            @RequestParam(required = false) String indicaciones, @RequestParam(required = false) String fecha,
            RedirectAttributes flash) {
        try {
            indicadas.indicar(id, new IndicacionComando(idTipo, duracion, fechaRecomendada, null, indicaciones),
                usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Cita de seguimiento indicada: recepción la verá en «Citas pendientes».");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/medico/agenda" + (fecha == null || fecha.isBlank() ? "" : "?fecha=" + fecha);
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
