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
    private final pe.consultorio.citas.service.RecuperacionService recuperacion;
    private final pe.consultorio.citas.service.CitaIndicadaService indicadas;

    public FichaPacienteController(PacienteService pacienteService,
            pe.consultorio.citas.service.RecuperacionService recuperacion,
            pe.consultorio.citas.service.CitaIndicadaService indicadas) {
        this.pacienteService = pacienteService;
        this.recuperacion = recuperacion;
        this.indicadas = indicadas;
    }

    /** El paciente olvidó su contraseña: enlace de un solo uso para su cuenta web propia (sin correo). */
    @PostMapping("/{id}/enlace-recuperacion")
    public String enlaceRecuperacion(@org.springframework.security.core.annotation.AuthenticationPrincipal
            pe.consultorio.citas.config.UsuarioSesion usuario, @PathVariable Long id, RedirectAttributes flash) {
        try {
            String codigo = recuperacion.generarParaPaciente(id, usuario.getIdCuenta());
            flash.addFlashAttribute("enlace", org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/recuperar/cambiar").queryParam("codigo", codigo).toUriString());
        } catch (pe.consultorio.citas.exception.ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recepcion/pacientes/" + id;
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
        model.addAttribute("indicadas", indicadas.pendientesVistaDePaciente(id));
        return "recepcion/pacientes";
    }

    /**
    Registrar paciente. {@code volver=cita}: se abrió desde la nueva cita; al guardar vuelve a ella con el paciente ya
    elegido. {@code q}: lo que se buscó, para no escribirlo dos veces (número → documento; texto → nombres).
    */
    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) String volver, @RequestParam(required = false) String q,
            Model model) {
        PacienteForm form = new PacienteForm();
        if (q != null && !q.isBlank()) {
            if (q.trim().matches("[0-9A-Za-z]*\\d[0-9A-Za-z]*")) {
                form.setNroDocumento(q.trim());
            } else {
                form.setNombres(q.trim());
            }
        }
        model.addAttribute("form", form);
        model.addAttribute("volver", "cita".equals(volver) ? "cita" : null);
        return "recepcion/paciente-form";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("form") PacienteForm form, BindingResult errores,
            @RequestParam(required = false) String volver, Model model, RedirectAttributes flash) {
        model.addAttribute("volver", "cita".equals(volver) ? "cita" : null);
        if (errores.hasErrors()) {
            return "recepcion/paciente-form";
        }
        try {
            Long id = pacienteService.registrar(form);
            flash.addFlashAttribute("exito", "Paciente registrado.");
            if ("cita".equals(volver)) {
                return "redirect:/recepcion/cita/nueva?idPaciente=" + id;
            }
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
