package pe.consultorio.citas.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.CuentaService;

/** La barra lateral muestra a cada rol solo su menú. */
@WebMvcTest(BarraLateralTest.ControladorDePrueba.class)
@Import({ SeguridadConfig.class, ManejadorLoginExitoso.class, ManejadorLoginFallido.class,
    AtributosGlobales.class, BarraLateralTest.ControladorDePrueba.class })
class BarraLateralTest {

    @Controller
    static class ControladorDePrueba {
        @GetMapping("/prueba/ejemplo")
        String ejemplo() {
            return "fragmentos/ejemplo";
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CuentaService cuentaService;

    @MockitoBean
    ConfiguracionService configuracionService;

    @Test
    void elPacienteVeSoloSuMenu() throws Exception {
        mvc.perform(get("/prueba/ejemplo").with(user("p").roles("PACIENTE")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Mis citas")))
            .andExpect(content().string(not(containsString("Confirmaciones"))));
    }

    @Test
    void elAdministradorVeRecepcionYAdministracion() throws Exception {
        mvc.perform(get("/prueba/ejemplo").with(user("a").roles("ADMINISTRADOR")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Confirmaciones")))
            .andExpect(content().string(containsString("Reportes")))
            .andExpect(content().string(not(containsString("Mi horario"))));
    }
}
