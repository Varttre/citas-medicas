package pe.consultorio.citas.controller.publico;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.config.ManejadorLoginExitoso;
import pe.consultorio.citas.config.ManejadorLoginFallido;
import pe.consultorio.citas.config.SeguridadConfig;
import pe.consultorio.citas.service.CuentaService;

/** Prueba de la capa web sin base de datos: la página pública es accesible sin sesión. */
@WebMvcTest(InicioController.class)
@Import({SeguridadConfig.class, ManejadorLoginExitoso.class, ManejadorLoginFallido.class})
class InicioControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CuentaService cuentaService;

    @Test
    void laPaginaPublicaNoPideSesion() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("publico/inicio"));
    }

    @Test
    void elPortalDelPacientePideSesion() throws Exception {
        mvc.perform(get("/paciente/inicio"))
            .andExpect(status().is3xxRedirection());
    }
}
