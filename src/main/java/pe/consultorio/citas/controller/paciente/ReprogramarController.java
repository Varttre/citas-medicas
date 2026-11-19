package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.servlet.http.HttpSession;
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
import pe.consultorio.citas.dto.CitaReprogramable;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.dto.ReprogramarSesion;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.ReprogramacionService;
import pe.consultorio.citas.service.disponibilidad.BloqueoTemporalService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;

/**
Reprogramar desde «Mis citas», en dos pasos: elegir el nuevo horario del mismo médico (queda retenido unos minutos)
y confirmar. No hay plazo de antelación. Mientras el paciente decide, su cita actual sigue en pie.
*/
@Controller
@RequestMapping("/paciente/citas/{id}/reprogramar")
public class ReprogramarController {

    static final String SESION = "reprogramar";
    private static final String NO_SE_PUEDE = "Esta cita ya no se puede reprogramar.";

    private final PortalPacienteService portal;
    private final DisponibilidadService disponibilidad;
    private final BloqueoTemporalService bloqueos;
    private final ReprogramacionService reprogramacion;
    private final PreferenciaService preferencias;
    private final Clock reloj;

    public ReprogramarController(PortalPacienteService portal, DisponibilidadService disponibilidad,
            BloqueoTemporalService bloqueos, ReprogramacionService reprogramacion, PreferenciaService preferencias,
            Clock reloj) {
        this.portal = portal;
        this.disponibilidad = disponibilidad;
        this.bloqueos = bloqueos;
        this.reprogramacion = reprogramacion;
        this.preferencias = preferencias;
        this.reloj = reloj;
    }

    /** Paso 1: horarios del mismo médico, con la duración de la cita, desde la fecha elegida (hoy por defecto). */
    @GetMapping
    public String horarios(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Model model, RedirectAttributes flash) {
        CitaReprogramable c;
        try {
            c = portal.reprogramable(usuario.getIdCuenta(), id);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", NO_SE_PUEDE);
            return "redirect:/paciente/citas";
        }
        LocalDate desde = fecha != null ? fecha : LocalDate.now(reloj);
        model.addAttribute("c", c);
        model.addAttribute("fecha", desde);
        model.addAttribute("dias", disponibilidad.ofertasProximas(c.idMedico(), c.duracionMin(), desde,
            usuario.getIdCuenta()));
        return "paciente/reprogramar-1";
    }

    /** Retiene el horario elegido y pasa a confirmar. La cita actual no cambia todavía. */
    @PostMapping("/bloqueo")
    public String elegir(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime nuevoInicio,
            HttpSession sesion, RedirectAttributes flash) {
        try {
            CitaReprogramable c = portal.reprogramable(usuario.getIdCuenta(), id);
            bloqueos.tomar(usuario.getIdCuenta(), c.idMedico(), nuevoInicio, nuevoInicio.plusMinutes(c.duracionMin()));
        } catch (HorarioOcupadoException e) {
            flash.addFlashAttribute("error", "Ese horario acaba de ser ocupado. Elige otro.");
            return "redirect:/paciente/citas/" + id + "/reprogramar";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", NO_SE_PUEDE);
            return "redirect:/paciente/citas";
        }
        sesion.setAttribute(SESION, new ReprogramarSesion(id, nuevoInicio));
        return "redirect:/paciente/citas/" + id + "/reprogramar/confirmar";
    }

    /** Paso 2: horario actual → nuevo, datos que se conservan y preferencia de disponibilidad inmediata prellenada. */
    @GetMapping("/confirmar")
    public String confirmar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id, HttpSession sesion,
            Model model, RedirectAttributes flash) {
        ReprogramarSesion r = enCurso(sesion, id);
        if (r == null) {
            return "redirect:/paciente/citas/" + id + "/reprogramar";
        }
        CitaReprogramable c;
        try {
            c = portal.reprogramable(usuario.getIdCuenta(), id);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", NO_SE_PUEDE);
            return "redirect:/paciente/citas";
        }
        model.addAttribute("c", c);
        model.addAttribute("nuevoInicio", r.nuevoInicio());
        model.addAttribute("nuevoFin", r.nuevoInicio().plusMinutes(c.duracionMin()));
        model.addAttribute("segundos", bloqueos.vigenteDe(usuario.getIdCuenta())
            .map(bloqueos::segundosRestantes).orElse(0L));
        return "paciente/reprogramar-2";
    }

    /** Reprograma la cita y actualiza (o retira) su preferencia de disponibilidad inmediata. */
    @PostMapping
    public String reprogramar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean aceptaLlamada,
            @RequestParam(required = false) AlcancePreferencia alcance,
            @RequestParam(required = false) Integer minLlegada, @RequestParam(required = false) Integer diasPrevios,
            HttpSession sesion, RedirectAttributes flash) {
        ReprogramarSesion r = enCurso(sesion, id);
        if (r == null) {
            return "redirect:/paciente/citas/" + id + "/reprogramar";
        }
        CitaReprogramable c;
        try {
            c = portal.reprogramable(usuario.getIdCuenta(), id);
        } catch (ReglaNegocioException e) {
            sesion.removeAttribute(SESION);
            flash.addFlashAttribute("error", NO_SE_PUEDE);
            return "redirect:/paciente/citas";
        }
        PreferenciaDatos nueva = null;
        if (aceptaLlamada) {
            if (alcance == null || minLlegada == null || minLlegada < 0 || diasPrevios == null || diasPrevios < 0) {
                flash.addFlashAttribute("error", "Completa los datos de la disponibilidad inmediata o desmarca la casilla.");
                return "redirect:/paciente/citas/" + id + "/reprogramar/confirmar";
            }
            nueva = new PreferenciaDatos(alcance, minLlegada, diasPrevios);
        }
        try {
            reprogramacion.reprogramar(id, r.nuevoInicio(), usuario.getIdCuenta());
        } catch (HorarioOcupadoException e) {
            flash.addFlashAttribute("error", "Ese horario acaba de ser ocupado. Tu cita actual sigue en pie; "
                + "actualizamos los horarios disponibles.");
            return "redirect:/paciente/citas/" + id + "/reprogramar";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/paciente/citas/" + id + "/reprogramar";
        }
        if (nueva != null && !nueva.equals(c.preferencia())) {
            preferencias.guardar(id, nueva, usuario.getIdCuenta());
        } else if (nueva == null && c.preferencia() != null) {
            preferencias.revocar(id, usuario.getIdCuenta());
        }
        bloqueos.liberar(usuario.getIdCuenta());
        sesion.removeAttribute(SESION);
        flash.addFlashAttribute("exito", "Cita reprogramada. El consultorio te llamará para confirmarla; tu horario "
            + "anterior ya está disponible para otros pacientes.");
        return "redirect:/paciente/citas";
    }

    /** El paciente se queda con su horario: se suelta la retención y no cambia nada. */
    @PostMapping("/mantener")
    public String mantener(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            HttpSession sesion) {
        bloqueos.liberar(usuario.getIdCuenta());
        sesion.removeAttribute(SESION);
        return "redirect:/paciente/citas";
    }

    private static ReprogramarSesion enCurso(HttpSession sesion, Long id) {
        return sesion.getAttribute(SESION) instanceof ReprogramarSesion r && r.idCita().equals(id) ? r : null;
    }
}
