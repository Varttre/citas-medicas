package pe.consultorio.citas.controller.recepcion;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
Pantallas de recepción (también las usa el administrador). Por ahora muestran el diseño del prototipo con datos de
ejemplo; cada cola y la agenda se conectan a sus servicios en las tareas B1 a B9.
*/
@Controller
@RequestMapping("/recepcion")
public class RecepcionController {

    @GetMapping("/inicio")
    public String inicio() {
        return "recepcion/inicio";
    }

    @GetMapping("/cita/nueva")
    public String nuevaCita() {
        return "recepcion/nueva-cita";
    }

    @GetMapping("/confirmaciones")
    public String confirmaciones() {
        return "recepcion/confirmaciones";
    }

    @GetMapping("/cupos")
    public String cupos() {
        return "recepcion/cupos";
    }

    @GetMapping("/pendientes")
    public String pendientes() {
        return "recepcion/pendientes";
    }
}
