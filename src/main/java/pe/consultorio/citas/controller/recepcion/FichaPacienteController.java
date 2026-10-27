package pe.consultorio.citas.controller.recepcion;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.dto.PacienteForm;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.PacienteService;

/** Pacientes en recepción: buscar, ver la ficha, registrar, editar y revocar cuentas vinculadas. */
@Controller
@RequestMapping("/recepcion/pacientes")
public class FichaPacienteController {

    private final PacienteService pacienteService;

    public FichaPacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @ModelAttribute("tiposDocumento")
    public TipoDocumento[] tiposDocumento() {
        return TipoDocumento.values();
    }

    @GetMapping
    public String buscar(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("resultados", pacienteService.buscar(q));
        return "recepcion/pacientes";
    }

    @GetMapping("/{id}")
    public String ficha(@PathVariable Long id, @RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("resultados", pacienteService.buscar(q));
        model.addAttribute("ficha", pacienteService.ficha(id));
        return "recepcion/pacientes";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("form", new PacienteForm());
        return "recepcion/paciente-form";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("form") PacienteForm form, BindingResult errores, Model model,
            RedirectAttributes flash) {
        if (errores.hasErrors()) {
            return "recepcion/paciente-form";
        }
        try {
            Long id = pacienteService.registrar(form);
            flash.addFlashAttribute("exito", "Paciente registrado.");
            return "redirect:/recepcion/pacientes/" + id;
        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            return "recepcion/paciente-form";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("idPaciente", id);
        model.addAttribute("form", pacienteService.formulario(id));
        return "recepcion/paciente-form";
    }

    @PostMapping("/{id}")
    public String guardar(@PathVariable Long id, @Valid @ModelAttribute("form") PacienteForm form,
            BindingResult errores, Model model, RedirectAttributes flash) {
        model.addAttribute("idPaciente", id);
        if (errores.hasErrors()) {
            return "recepcion/paciente-form";
        }
        try {
            pacienteService.editar(id, form);
            flash.addFlashAttribute("exito", "Datos del paciente actualizados.");
            return "redirect:/recepcion/pacientes/" + id;
        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            return "recepcion/paciente-form";
        }
    }

    @PostMapping("/{id}/cuentas/{idCuenta}/revocar")
    public String revocar(@PathVariable Long id, @PathVariable Long idCuenta, RedirectAttributes flash) {
        pacienteService.revocarCuenta(id, idCuenta);
        flash.addFlashAttribute("exito", "La cuenta ya no puede agendar por este paciente.");
        return "redirect:/recepcion/pacientes/" + id;
    }
}
