package pe.consultorio.citas.controller.medico;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
Pantallas del portal del médico. Inicio, agenda y estadísticas muestran por ahora el diseño del prototipo con datos
de ejemplo; se conectan a los servicios en las tareas de agenda (B9) y estadísticas (D6).
*/
@Controller
@RequestMapping("/medico")
public class MedicoController {

    static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-PE"));

    private final Clock reloj;

    public MedicoController(Clock reloj) {
        this.reloj = reloj;
    }

    @GetMapping("/inicio")
    public String inicio(Model model) {
        String fecha = LocalDate.now(reloj).format(FECHA_LARGA);
        model.addAttribute("fechaHoy", Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        return "medico/inicio";
    }

    @GetMapping("/estadisticas")
    public String estadisticas() {
        return "medico/estadisticas";
    }
}
