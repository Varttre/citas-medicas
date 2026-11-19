package pe.consultorio.citas.controller.recepcion;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.consultorio.citas.service.TableroRecepcionService;

/**
Pantallas de recepción (también las usa el administrador): el inicio con el trabajo pendiente y, por ahora con el
diseño del prototipo, la nueva cita desde recepción y las citas indicadas pendientes. Las colas, la agenda y los
pacientes tienen su propio controlador.
*/
@Controller
@RequestMapping("/recepcion")
public class RecepcionController {

    private final TableroRecepcionService tablero;

    public RecepcionController(TableroRecepcionService tablero) {
        this.tablero = tablero;
    }

    /** Trabajo pendiente: seis contadores enlazados a su pantalla y cómo va cada médico hoy. */
    @GetMapping("/inicio")
    public String inicio(Model model) {
        model.addAttribute("t", tablero.tablero());
        return "recepcion/inicio";
    }

    @GetMapping("/cita/nueva")
    public String nuevaCita() {
        return "recepcion/nueva-cita";
    }

    @GetMapping("/pendientes")
    public String pendientes() {
        return "recepcion/pendientes";
    }
}
