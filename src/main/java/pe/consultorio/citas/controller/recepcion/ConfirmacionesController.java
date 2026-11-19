package pe.consultorio.citas.controller.recepcion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.ColaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.LlamadaService;
import pe.consultorio.citas.service.PortalPacienteService;

/**
Cola de confirmaciones: las citas programadas de las próximas horas que recepción debe llamar para confirmar. Es el
único lugar donde una cita se confirma, siempre con la llamada registrada.
*/
@Controller
@RequestMapping("/recepcion/confirmaciones")
public class ConfirmacionesController {

    static final Map<String, String> RESULTADOS = new LinkedHashMap<>();
    static {
        RESULTADOS.put(ResultadoLlamada.CONFIRMO.name(), "Confirmó → la cita pasa a «confirmada»");
        RESULTADOS.put(ResultadoLlamada.NO_CONTESTO.name(), "No contestó (sigue en la cola, suma un intento)");
        RESULTADOS.put(ResultadoLlamada.CANCELO.name(), "Canceló → cancelada por el paciente, libera el horario");
        RESULTADOS.put(ResultadoLlamada.REPROGRAMO.name(), "Reprogramó → abre la búsqueda de horarios");
        RESULTADOS.put(ResultadoLlamada.NUMERO_INVALIDO.name(), "Número inválido → el contacto queda marcado para actualizar");
    }

    private final ColaService colas;
    private final LlamadaService llamadas;
    private final PortalPacienteService portal;
    private final ConfiguracionService configuracion;

    public ConfirmacionesController(ColaService colas, LlamadaService llamadas, PortalPacienteService portal,
            ConfiguracionService configuracion) {
        this.colas = colas;
        this.llamadas = llamadas;
        this.portal = portal;
        this.configuracion = configuracion;
    }

    @GetMapping
    public String cola(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad,
            Model model) {
        model.addAttribute("filas", colas.confirmaciones(fecha, medico, especialidad));
        model.addAttribute("resultados", RESULTADOS);
        model.addAttribute("fecha", fecha);
        model.addAttribute("medico", medico);
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("medicos", portal.medicos(null));
        model.addAttribute("especialidades", configuracion.especialidadesActivas());
        model.addAttribute("horas", configuracion.parametros().getAnticipacionConfirmH());
        return "recepcion/confirmaciones";
    }

    @PostMapping("/{idCita}/resultado")
    public String resultado(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long idCita,
            @RequestParam ResultadoLlamada resultado, @RequestParam(required = false) String observacion,
            RedirectAttributes flash) {
        try {
            llamadas.registrarConfirmacion(idCita, resultado, observacion, usuario.getIdCuenta());
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/recepcion/confirmaciones";
        }
        switch (resultado) {
            case CONFIRMO -> flash.addFlashAttribute("exito", "Cita confirmada.");
            case CANCELO -> flash.addFlashAttribute("exito", "Cita cancelada. El horario ya se ofrece a otros pacientes.");
            case REPROGRAMO -> {
                flash.addFlashAttribute("aviso", "Llamada registrada. Elige el nuevo horario de la cita.");
                return "redirect:/recepcion/citas/" + idCita + "/reprogramar";
            }
            default -> flash.addFlashAttribute("exito", "Llamada registrada.");
        }
        return "redirect:/recepcion/confirmaciones";
    }
}
