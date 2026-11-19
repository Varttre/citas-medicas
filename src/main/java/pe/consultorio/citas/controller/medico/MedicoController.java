package pe.consultorio.citas.controller.medico;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.service.EstadisticaMedicoService;
import pe.consultorio.citas.service.TableroMedicoService;

/**
Inicio y estadísticas del médico, siempre con las citas del médico de la sesión. La agenda y el horario tienen su
propio controlador.
*/
@Controller
@RequestMapping("/medico")
public class MedicoController {

    static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
        Locale.forLanguageTag("es-PE"));

    private final Clock reloj;
    private final TableroMedicoService tablero;
    private final EstadisticaMedicoService estadisticas;

    public MedicoController(Clock reloj, TableroMedicoService tablero, EstadisticaMedicoService estadisticas) {
        this.reloj = reloj;
        this.tablero = tablero;
        this.estadisticas = estadisticas;
    }

    /** Contadores de hoy, próxima cita, puntualidad y alertas, solo con las citas del médico de la sesión. */
    @GetMapping("/inicio")
    public String inicio(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        String fecha = LocalDate.now(reloj).format(FECHA_LARGA);
        model.addAttribute("fechaHoy", Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        model.addAttribute("t", usuario == null || usuario.getIdMedico() == null ? null
            : tablero.tablero(usuario.getIdMedico(), usuario.getIdCuenta()));
        return "medico/inicio";
    }

    /** Solo con las citas del médico de la sesión: nunca datos de otros médicos ni comparativas. */
    @GetMapping("/estadisticas")
    public String estadisticas(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
        model.addAttribute("e", usuario == null || usuario.getIdMedico() == null ? null
            : estadisticas.calcular(usuario.getIdMedico()));
        return "medico/estadisticas";
    }
}
