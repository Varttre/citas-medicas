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
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.CuentaService;
import pe.consultorio.citas.service.SitioPublicoService;

/** Prueba de la capa web sin base de datos: la página pública es accesible sin sesión. */
@WebMvcTest(InicioController.class)
@Import({SeguridadConfig.class, ManejadorLoginExitoso.class, ManejadorLoginFallido.class})
class InicioControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CuentaService cuentaService;

    @MockitoBean
    ConfiguracionService configuracionService;

    @MockitoBean
    SitioPublicoService sitioPublicoService;

    @Test
    void laPaginaPublicaNoPideSesion() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("publico/inicio"));
    }

    /** Las dos subpáginas públicas, a las que llevan los avisos, también se abren sin sesión. */
    @Test
    void lasSubpaginasPublicasNoPidenSesion() throws Exception {
        mvc.perform(get("/especialidades"))
            .andExpect(status().isOk())
            .andExpect(view().name("publico/especialidades"));
        mvc.perform(get("/como-agendar"))
            .andExpect(status().isOk())
            .andExpect(view().name("publico/como-agendar"));
    }

    @Test
    void elPortalDelPacientePideSesion() throws Exception {
        mvc.perform(get("/paciente/inicio"))
            .andExpect(status().is3xxRedirection());
    }
}
