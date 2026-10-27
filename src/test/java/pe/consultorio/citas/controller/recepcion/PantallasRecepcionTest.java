package pe.consultorio.citas.controller.recepcion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.PostgresTestConfig;

/** Todas las pantallas de recepción cargan para recepción y para el administrador; un paciente no entra. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PantallasRecepcionTest {

    static final String[] RUTAS = {"/recepcion/inicio", "/recepcion/agenda", "/recepcion/cita/nueva",
        "/recepcion/confirmaciones", "/recepcion/cupos", "/recepcion/pendientes", "/recepcion/pacientes"};

    @Autowired
    MockMvc mvc;

    @Test
    void recepcionYAdministradorVenTodas() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).with(user("r@prueba.pe").roles("RECEPCION"))).andExpect(status().isOk());
            mvc.perform(get(ruta).with(user("a@prueba.pe").roles("ADMINISTRADOR"))).andExpect(status().isOk());
        }
    }

    @Test
    void unPacienteNoEntra() throws Exception {
        mvc.perform(get("/recepcion/inicio").with(user("p@prueba.pe").roles("PACIENTE")))
            .andExpect(status().isForbidden());
    }
}
