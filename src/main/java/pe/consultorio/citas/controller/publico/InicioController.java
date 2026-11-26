package pe.consultorio.citas.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.consultorio.citas.service.SitioPublicoService;

/** Página pública del consultorio y sus dos subpáginas, a las que llevan también los avisos del paciente. */
@Controller
public class InicioController {

    private final SitioPublicoService sitio;

    public InicioController(SitioPublicoService sitio) {
        this.sitio = sitio;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("especialidades", sitio.especialidades());
        return "publico/inicio";
    }

    /** Especialidades activas con sus médicos. */
    @GetMapping("/especialidades")
    public String especialidades(Model model) {
        model.addAttribute("especialidades", sitio.especialidades());
        return "publico/especialidades";
    }

    /** Pasos para agendar y preguntas frecuentes. */
    @GetMapping("/como-agendar")
    public String comoAgendar() {
        return "publico/como-agendar";
    }
}
