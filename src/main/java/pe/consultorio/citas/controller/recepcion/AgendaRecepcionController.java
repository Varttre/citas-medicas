package pe.consultorio.citas.controller.recepcion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

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
import pe.consultorio.citas.dto.CitaAgenda;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.AgendaService;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.ReprogramacionService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;

/**
Agenda del día de recepción: citas de todos los médicos, llegada, inasistencia, retiro, cancelación, reprogramación
y ajuste de duración.
*/
@Controller
@RequestMapping("/recepcion")
public class AgendaRecepcionController {

    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    private final AgendaService agenda;
    private final CitaEstadoService estados;
    private final ReprogramacionService reprogramacion;
    private final DisponibilidadService disponibilidad;
    private final ConfiguracionService configuracion;
    private final PortalPacienteService portal;
    private final Clock reloj;

    public AgendaRecepcionController(AgendaService agenda, CitaEstadoService estados,
            ReprogramacionService reprogramacion, DisponibilidadService disponibilidad,
            ConfiguracionService configuracion, PortalPacienteService portal, Clock reloj) {
        this.agenda = agenda;
        this.estados = estados;
        this.reprogramacion = reprogramacion;
        this.disponibilidad = disponibilidad;
        this.configuracion = configuracion;
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
        model.addAttribute("duraciones", duraciones());
        return "recepcion/agenda";
    }

    /** Horarios libres del mismo médico, con la duración de la cita, para el día elegido; los de calce perfecto, destacados. */
    @GetMapping("/citas/{id}/reprogramar")
    public String horarios(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Model model, RedirectAttributes flash) {
        CitaAgenda c = agenda.una(id);
        if (!c.vigente() || !c.inicio().isAfter(LocalDateTime.now(reloj))) {
            flash.addFlashAttribute("error", "Esta cita ya no se puede reprogramar.");
            return volver(c.inicio().toLocalDate().toString());
        }
        LocalDate dia = fecha != null ? fecha : c.inicio().toLocalDate();
        model.addAttribute("c", c);
        model.addAttribute("fecha", dia);
        model.addAttribute("ofertas", disponibilidad.ofertasDelDia(c.idMedico(), (int) c.duracionMin(), dia,
            usuario.getIdCuenta()));
        return "recepcion/reprogramar";
    }

    @PostMapping("/citas/{id}/reprogramar")
    public String reprogramar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime nuevoInicio,
            RedirectAttributes flash) {
        try {
            reprogramacion.reprogramar(id, nuevoInicio, usuario.getIdCuenta());
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/recepcion/citas/" + id + "/reprogramar?fecha=" + nuevoInicio.toLocalDate();
        }
        flash.addFlashAttribute("exito", "Cita reprogramada para el " + nuevoInicio.format(DIA_HORA)
            + ". Vuelve a quedar programada: hay que confirmarla otra vez.");
        return volver(nuevoInicio.toLocalDate().toString());
    }

    /** Cambia la duración de la cita (solo si el tipo de consulta lo permite a recepción). */
    @PostMapping("/citas/{id}/duracion")
    public String duracion(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam int nuevaDuracion, @RequestParam(required = false) String fecha,
            RedirectAttributes flash) {
        try {
            estados.ajustarDuracion(id, nuevaDuracion, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Duración cambiada a " + nuevaDuracion + " minutos.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(fecha);
    }

    private List<Integer> duraciones() {
        int paso = configuracion.incremento();
        return IntStream.rangeClosed(1, 240 / paso).map(i -> i * paso).boxed().toList();
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
