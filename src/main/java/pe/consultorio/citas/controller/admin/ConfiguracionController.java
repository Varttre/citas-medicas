package pe.consultorio.citas.controller.admin;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.AvisoForm;
import pe.consultorio.citas.dto.EspecialidadForm;
import pe.consultorio.citas.dto.ParametrosForm;
import pe.consultorio.citas.dto.PlazosForm;
import pe.consultorio.citas.dto.TipoConsultaForm;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.ConfiguracionService.ConfirmacionRequeridaException;

/**
Configuración del administrador en cuatro pestañas: tipos de consulta, parámetros globales, plazos por estado y
catálogos (especialidades y avisos del portal). Cada formulario guarda por separado y vuelve a su pestaña.
*/
@Controller
@RequestMapping("/admin/configuracion")
public class ConfiguracionController {

    private static final List<Integer> DURACIONES = java.util.stream.IntStream.rangeClosed(1, 16)
        .map(i -> i * 15).boxed().toList();
    private static final List<Integer> INCREMENTOS = List.of(15, 30, 45, 60);

    private final ConfiguracionService configuracion;

    public ConfiguracionController(ConfiguracionService configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping
    public String ver(@RequestParam(defaultValue = "tipos") String pestana, Model model) {
        model.addAttribute("pestana", pestana);
        model.addAttribute("tipos", configuracion.todosLosTipos());
        model.addAttribute("tiposWeb", configuracion.tiposWeb());
        model.addAttribute("p", configuracion.parametros());
        model.addAttribute("huecoMinimo", configuracion.huecoMinimoUtil());
        model.addAttribute("plazoNoAsistio", minutos(configuracion.plazo(Transicion.NO_ASISTIO).getMinutosDesde()));
        model.addAttribute("plazoCancelar",
            -minutos(configuracion.plazo(Transicion.CANCELADA_POR_PACIENTE).getMinutosHasta()));
        model.addAttribute("plazoLlegada", -minutos(configuracion.plazo(Transicion.ASISTIO).getMinutosDesde()));
        model.addAttribute("especialidades", configuracion.todasLasEspecialidades());
        model.addAttribute("avisos", configuracion.todosLosAvisos());
        model.addAttribute("duraciones", DURACIONES);
        model.addAttribute("incrementos", INCREMENTOS);
        model.addAttribute("modalidades", ModalidadPermitida.values());
        return "admin/configuracion";
    }

    private static int minutos(Short valor) {
        return valor == null ? 0 : valor;
    }

    @PostMapping("/tipos")
    public String crearTipo(@AuthenticationPrincipal UsuarioSesion usuario, @ModelAttribute TipoConsultaForm form,
            RedirectAttributes flash) {
        return guardarTipo(usuario, null, form, flash);
    }

    @PostMapping("/tipos/{id}")
    public String editarTipo(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Short id,
            @ModelAttribute TipoConsultaForm form, RedirectAttributes flash) {
        return guardarTipo(usuario, id, form, flash);
    }

    /** Segundo paso de «desactivar un tipo con citas futuras»: el administrador ya vio la advertencia. */
    @PostMapping("/tipos/{id}/desactivar")
    public String desactivarTipo(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Short id,
            RedirectAttributes flash) {
        try {
            configuracion.desactivarTipo(id, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Tipo de consulta desactivado. Sus citas futuras siguen en pie.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=tipos";
    }

    private String guardarTipo(UsuarioSesion usuario, Short id, TipoConsultaForm form, RedirectAttributes flash) {
        try {
            configuracion.guardarTipo(id, form, false, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", id == null ? "Tipo de consulta creado." : "Tipo de consulta guardado.");
        } catch (ConfirmacionRequeridaException e) {
            List<Cita> proximas = configuracion.citasFuturasDelTipo(id).stream().limit(5).toList();
            flash.addFlashAttribute("confirmarTipo", id);
            flash.addFlashAttribute("confirmarCitas", e.getCitasFuturas());
            flash.addFlashAttribute("confirmarProximas", proximas);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=tipos";
    }

    @PostMapping("/parametros")
    public String parametros(@AuthenticationPrincipal UsuarioSesion usuario, @ModelAttribute ParametrosForm form,
            RedirectAttributes flash) {
        try {
            configuracion.guardarParametros(form, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Parámetros guardados.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=parametros";
    }

    @PostMapping("/plazos")
    public String plazos(@AuthenticationPrincipal UsuarioSesion usuario, @ModelAttribute PlazosForm form,
            RedirectAttributes flash) {
        try {
            configuracion.guardarPlazos(form, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", "Plazos guardados.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=plazos";
    }

    @PostMapping({"/especialidades", "/especialidades/{id}"})
    public String especialidad(@AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable(required = false) Short id, @ModelAttribute EspecialidadForm form,
            RedirectAttributes flash) {
        try {
            configuracion.guardarEspecialidad(id, form, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", id == null ? "Especialidad creada." : "Especialidad guardada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=catalogos";
    }

    @PostMapping({"/avisos", "/avisos/{id}"})
    public String aviso(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable(required = false) Long id,
            @ModelAttribute AvisoForm form, RedirectAttributes flash) {
        try {
            configuracion.guardarAviso(id, form, usuario.getIdCuenta());
            flash.addFlashAttribute("exito", id == null ? "Aviso creado." : "Aviso guardado.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/configuracion?pestana=catalogos";
    }
}
