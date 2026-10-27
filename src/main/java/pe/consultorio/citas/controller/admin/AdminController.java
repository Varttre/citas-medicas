package pe.consultorio.citas.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
Pantallas de administración. Por ahora muestran el diseño del prototipo con datos de ejemplo; la configuración,
los usuarios y los reportes se conectan a sus servicios en las tareas D3, D4 y D5.
*/
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/configuracion")
    public String configuracion() {
        return "admin/configuracion";
    }

    @GetMapping("/usuarios")
    public String usuarios() {
        return "admin/usuarios";
    }

    @GetMapping("/reportes")
    public String reportes() {
        return "admin/reportes";
    }
}
