package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.CitaPaciente;
import pe.consultorio.citas.dto.ParaQuien;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.PreferenciaService;

/**
Pantallas del portal del paciente: inicio, Mis citas (con las agendadas para otras personas y la disponibilidad
inmediata de cada cita), las citas indicadas por el médico pendientes de agendar, «Mis terceros», citas pasadas,
avisos y WhatsApp. La reprogramación la atiende ReprogramarController, la
nueva cita NuevaCitaController y el perfil, PerfilController.
*/
@Controller
@RequestMapping("/paciente")
public class PacienteController {

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-PE"));

    private final Clock reloj;
    private final PortalPacienteService portal;
    private final PreferenciaService preferencias;
    private final ConfiguracionService configuracion;
    private final CitaIndicadaService indicadas;
    private final RegistroService registro;

    public PacienteController(Clock reloj, PortalPacienteService portal, PreferenciaService preferencias,
            ConfiguracionService configuracion, CitaIndicadaService indicadas, RegistroService registro) {
        this.indicadas = indicadas;
        this.registro = registro;
        this.reloj = reloj;
        this.portal = portal;
        this.preferencias = preferencias;
        this.configuracion = configuracion;
    }

    /**
    Saludo, las citas indicadas por el médico que faltan agendar (siempre a la vista, no como aviso), las tres próximas
    citas que ve la cuenta y los avisos vigentes, plegados en la campana del encabezado.
    */
    @GetMapping("/inicio")
    public String inicio(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        String fecha = LocalDate.now(reloj).format(FECHA_LARGA);
        model.addAttribute("fechaHoy", Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        model.addAttribute("proximas", usuario == null ? List.of()
            : portal.proximas(usuario.getIdCuenta()).stream().limit(3).toList());
        model.addAttribute("avisos", configuracion.avisosActivos());
        model.addAttribute("indicadas", usuario == null ? List.of() : indicadas.pendientesDeCuenta(usuario.getIdCuenta()));
        return "paciente/inicio";
    }

    /** «Mis terceros»: las personas para las que la cuenta puede agendar, y añadir una nueva sin agendar todavía. */
    @GetMapping("/terceros")
    public String terceros(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("terceros", usuario == null ? List.of() : registro.terceros(usuario.getIdCuenta()));
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("parentescos", pe.consultorio.citas.entity.Parentesco.values());
        return "paciente/terceros";
    }

    @PostMapping("/terceros")
    public String anadirTercero(@AuthenticationPrincipal UsuarioSesion usuario, @ModelAttribute TerceroForm tercero,
            RedirectAttributes flash) {
        try {
            ParaQuien p = registro.vincularTercero(usuario.getIdCuenta(), tercero);
            if (!p.tercero()) {
                flash.addFlashAttribute("aviso", "Ese documento es el tuyo: tus citas ya están en «Mis citas».");
            } else {
                flash.addFlashAttribute("exito", p.nombre() + " ya está entre tus terceros. Puedes agendarle una cita.");
                if (p.aviso() != null) {
                    flash.addFlashAttribute("aviso", p.aviso());
                }
            }
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("tercero", tercero);
        }
        return "redirect:/paciente/terceros";
    }

    @GetMapping("/citas")
    public String misCitas(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("citas", usuario == null ? List.of() : portal.proximas(usuario.getIdCuenta()));
        model.addAttribute("indicadas", usuario == null ? List.of() : indicadas.pendientesDeCuenta(usuario.getIdCuenta()));
        ParametroConsultorio p = configuracion.parametros();
        model.addAttribute("penalizacion", p.isMostrarPenalizacion() ? p.getTextoPenalizacion() : null);
        return "paciente/mis-citas";
    }

    /** Activa o ajusta la disponibilidad inmediata de una cita vigente. No cambia la antigüedad en la lista. */
    @PostMapping("/citas/{id}/preferencia")
    public String guardarPreferencia(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam(required = false) AlcancePreferencia alcance, @RequestParam(required = false) Integer llegada,
            @RequestParam(defaultValue = "MINUTOS") String unidadLlegada,
            @RequestParam(required = false) Integer diasPrevios, RedirectAttributes flash) {
        CitaPaciente c = cambiable(usuario, id);
        if (c == null) {
            flash.addFlashAttribute("error", "Esta cita ya no admite cambios.");
        } else if (alcance == null || llegada == null || llegada < 0 || diasPrevios == null || diasPrevios < 0) {
            flash.addFlashAttribute("error", "Completa los tres datos de la disponibilidad inmediata.");
        } else {
            try {
                preferencias.guardar(id, new PreferenciaDatos(alcance,
                    "HORAS".equals(unidadLlegada) ? llegada * 60 : llegada, diasPrevios), usuario.getIdCuenta());
                flash.addFlashAttribute("exito", c.preferencia() == null
                    ? "Listo: te llamaremos si se libera un cupo antes de tu cita."
                    : "Disponibilidad inmediata actualizada. Tu antigüedad en la lista no cambia.");
            } catch (ReglaNegocioException e) {
                flash.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/paciente/citas";
    }

    @PostMapping("/citas/{id}/preferencia/revocar")
    public String revocarPreferencia(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        if (cambiable(usuario, id) == null) {
            flash.addFlashAttribute("error", "Esta cita ya no admite cambios.");
        } else {
            preferencias.revocar(id, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Ya no te llamaremos para adelantar esta cita.");
        }
        return "redirect:/paciente/citas";
    }

    /** La cita de «Mis citas» si la cuenta la ve y aún admite cambios; null si ya no; 404 si no la ve. */
    private CitaPaciente cambiable(UsuarioSesion usuario, Long id) {
        portal.visible(usuario.getIdCuenta(), id);
        return portal.proximas(usuario.getIdCuenta()).stream()
            .filter(c -> c.idCita().equals(id) && c.cancelable()).findFirst().orElse(null);
    }

    @GetMapping("/citas/pasadas")
    public String citasPasadas(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("citas", usuario == null ? List.of() : portal.pasadas(usuario.getIdCuenta()));
        return "paciente/citas-pasadas";
    }

    @GetMapping("/avisos")
    public String avisos(Model model) {
        model.addAttribute("avisos", configuracion.avisosActivos());
        return "paciente/avisos";
    }

    /** Detalle de un aviso: se entra desde la campana del inicio o desde la lista de avisos. */
    @GetMapping("/avisos/{id}")
    public String aviso(@PathVariable Long id, Model model) {
        model.addAttribute("avisoDetalle", configuracion.avisoActivo(id));
        return "paciente/aviso";
    }

    @GetMapping("/whatsapp")
    public String whatsapp(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("opciones", usuario == null ? List.of()
            : portal.whatsapp(usuario.getIdCuenta(), configuracion.parametros().getWhatsappNumero()));
        return "paciente/whatsapp";
    }
}
