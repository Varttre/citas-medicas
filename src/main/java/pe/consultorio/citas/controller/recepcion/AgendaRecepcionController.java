package pe.consultorio.citas.controller.recepcion;

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
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.AgendaService;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.PortalPacienteService;

/** Agenda del día de recepción: citas de todos los médicos, llegada, inasistencia, retiro y cancelación. */
@Controller
@RequestMapping("/recepcion")
public class AgendaRecepcionController {

    private final AgendaService agenda;
    private final CitaEstadoService estados;
    private final PortalPacienteService portal;
    private final Clock reloj;

    public AgendaRecepcionController(AgendaService agenda, CitaEstadoService estados, PortalPacienteService portal,
            Clock reloj) {
        this.agenda = agenda;
        this.estados = estados;
        this.portal = portal;
        this.reloj = reloj;
    }

    @GetMapping("/agenda")
    public String agenda(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long medico, Model model) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(reloj);
        model.addAttribute("fecha", dia);
        model.addAttribute("medico", medico);
        model.addAttribute("medicos", portal.medicos(null));
        model.addAttribute("citas", agenda.delDia(dia, medico));
        return "recepcion/agenda";
    }

    @PostMapping("/citas/{id}/llegada")
    public String llegada(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam(required = false) String fecha, RedirectAttributes flash) {
        try {
            estados.marcarLlegada(id, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Llegada registrada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(fecha);
    }

    /** Cancelar (por paciente o por el consultorio) o marcar inasistencia; desde «Asistió», inasistencia = retiro. */
    @PostMapping("/citas/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam EstadoCita nuevoEstado, @RequestParam(required = false) String motivo,
            @RequestParam(defaultValue = "false") boolean asistio, @RequestParam(required = false) String fecha,
            RedirectAttributes flash) {
        try {
            switch (nuevoEstado) {
                case CANCELADA_POR_PACIENTE -> estados.cancelarPorPaciente(id, usuario.getIdCuenta(), motivo);
                case CANCELADA_POR_CLINICA -> estados.cancelarPorClinica(id, usuario.getIdCuenta(), motivo);
                case NO_ASISTIO -> {
                    if (asistio) {
                        estados.marcarRetiro(id, usuario.getIdCuenta(), motivo);
                    } else {
                        estados.marcarNoAsistio(id, usuario.getIdCuenta());
                    }
                }
                default -> throw new ReglaNegocioException("Ese cambio de estado no se hace desde la agenda.");
            }
            flash.addFlashAttribute("exito", "Cita actualizada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(fecha);
    }

    private static String volver(String fecha) {
        return "redirect:/recepcion/agenda" + (fecha == null || fecha.isBlank() ? "" : "?fecha=" + fecha);
    }
}
