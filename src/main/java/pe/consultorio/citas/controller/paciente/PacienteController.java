package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.service.PortalPacienteService;

/**
Pantallas del portal del paciente. Mis citas y citas pasadas muestran datos reales; la reprogramación, los avisos y
el perfil muestran por ahora el diseño del prototipo y se conectan en las tareas C4, C5 y C9.
*/
@Controller
@RequestMapping("/paciente")
public class PacienteController {

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-PE"));

    private final Clock reloj;
    private final PortalPacienteService portal;

    public PacienteController(Clock reloj, PortalPacienteService portal) {
        this.reloj = reloj;
        this.portal = portal;
    }

    @GetMapping("/inicio")
    public String inicio(Model model) {
        String fecha = LocalDate.now(reloj).format(FECHA_LARGA);
        model.addAttribute("fechaHoy", Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        return "paciente/inicio";
    }

    /**
    Nueva cita, paso 1 (¿para quién?) y paso 3 (cita indicada). Los pasos 2, 4 y 5 los atiende
    NuevaCitaController.
    */
    @GetMapping("/cita/nueva")
    public String nuevaCita() {
        return "paciente/nueva-cita-1";
    }

    @GetMapping("/cita/nueva/3")
    public String nuevaCitaIndicada() {
        return "paciente/nueva-cita-3";
    }

    @GetMapping("/citas")
    public String misCitas(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("citas", usuario == null || usuario.getIdPacientePropio() == null ? List.of()
            : portal.proximas(usuario.getIdPacientePropio()));
        return "paciente/mis-citas";
    }

    @GetMapping("/citas/pasadas")
    public String citasPasadas(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("citas", usuario == null || usuario.getIdPacientePropio() == null ? List.of()
            : portal.pasadas(usuario.getIdPacientePropio()));
        return "paciente/citas-pasadas";
    }

    @GetMapping("/citas/reprogramar")
    public String reprogramar() {
        return "paciente/reprogramar-1";
    }

    @GetMapping("/citas/reprogramar/2")
    public String reprogramarHorario() {
        return "paciente/reprogramar-2";
    }

    @GetMapping("/avisos")
    public String avisos() {
        return "paciente/avisos";
    }

    @GetMapping("/whatsapp")
    public String whatsapp() {
        return "paciente/whatsapp";
    }

    @GetMapping("/perfil")
    public String perfil() {
        return "paciente/perfil";
    }
}
