package pe.consultorio.citas.controller.publico;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.consultorio.citas.dto.RegistroForm;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.RegistroService;

/** Registro de una cuenta de paciente desde la web pública. */
@Controller
@RequestMapping("/registro")
public class RegistroController {

    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @ModelAttribute("tiposDocumento")
    public TipoDocumento[] tiposDocumento() {
        return TipoDocumento.values();
    }

    @GetMapping
    public String formulario(Model model) {
        model.addAttribute("form", new RegistroForm());
        return "publico/registro";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("form") RegistroForm form, BindingResult errores, Model model) {
        if (errores.hasErrors()) {
            return "publico/registro";
        }
        try {
            registroService.registrar(form);
            return "redirect:/login?registrado";
        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            return "publico/registro";
        }
    }
}
