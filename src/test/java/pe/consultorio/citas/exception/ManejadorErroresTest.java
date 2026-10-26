package pe.consultorio.citas.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import pe.consultorio.citas.config.SeguridadConfig;

/** Comprueba qué página de error ve el usuario en cada caso. */
@WebMvcTest(ManejadorErroresTest.ControladorDePrueba.class)
@Import({ SeguridadConfig.class, ManejadorErroresTest.ControladorDePrueba.class })
@WithMockUser
class ManejadorErroresTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unaRutaInexistenteMuestraLaPagina404() throws Exception {
        mvc.perform(get("/no-existe"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }

    @Test
    void unRecursoNoEncontradoMuestraLaPagina404() throws Exception {
        mvc.perform(get("/prueba/no-encontrado"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }

    @Test
    void unaReglaDeNegocioMuestraSuMensaje() throws Exception {
        mvc.perform(get("/prueba/regla"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(view().name("error/negocio"))
            .andExpect(model().attribute("mensaje", HorarioOcupadoException.MENSAJE));
    }

    @Test
    void unErrorInesperadoMuestraUnCodigoYNoLaTraza() throws Exception {
        mvc.perform(get("/prueba/inesperado"))
            .andExpect(status().isInternalServerError())
            .andExpect(view().name("error/500"))
            .andExpect(model().attributeExists("idError"));
    }

    /** Controlador mínimo que solo existe en esta prueba para provocar cada tipo de error. */
    @Controller
    static class ControladorDePrueba {

        @GetMapping("/prueba/no-encontrado")
        String noEncontrado() {
            throw new RecursoNoEncontradoException("cita 99 de otro médico");
        }

        @GetMapping("/prueba/regla")
        String regla() {
            throw new HorarioOcupadoException();
        }

        @GetMapping("/prueba/inesperado")
        String inesperado() {
            throw new IllegalStateException("fallo simulado");
        }
    }
}
