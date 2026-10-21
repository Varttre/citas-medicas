package pe.consultorio.citas.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Página pública del consultorio. */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio() {
        return "publico/inicio";
    }
}
