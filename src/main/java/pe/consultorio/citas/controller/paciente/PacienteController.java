package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
Pantallas del portal del paciente. Por ahora muestran el diseño del prototipo con datos de ejemplo; la nueva cita,
mis citas, la reprogramación y el perfil se conectan a sus servicios en las tareas C4 a C9.
*/
@Controller
@RequestMapping("/paciente")
public class PacienteController {

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-PE"));

    private final Clock reloj;

    public PacienteController(Clock reloj) {
        this.reloj = reloj;
    }

    @GetMapping("/inicio")
    public String inicio(Model model) {
        String fecha = LocalDate.now(reloj).format(FECHA_LARGA);
        model.addAttribute("fechaHoy", Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        return "paciente/inicio";
    }

    /** Nueva cita en cinco pasos: paciente, especialidad, médico, horario y confirmación. */
    @GetMapping("/cita/nueva")
    public String nuevaCita() {
        return "paciente/nueva-cita-1";
    }

    @GetMapping("/cita/nueva/{paso:[2-5]}")
    public String nuevaCitaPaso(@PathVariable int paso) {
        return "paciente/nueva-cita-" + paso;
    }

    @GetMapping("/citas")
    public String misCitas() {
        return "paciente/mis-citas";
    }

    @GetMapping("/citas/pasadas")
    public String citasPasadas() {
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
