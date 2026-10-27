package pe.consultorio.citas.controller.paciente;

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

/** Todas las pantallas del paciente y las públicas de la cuenta cargan. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PantallasPacienteTest {

    static final String[] RUTAS = {"/paciente/inicio", "/paciente/cita/nueva", "/paciente/cita/nueva/2",
        "/paciente/cita/nueva/3", "/paciente/citas",
        "/paciente/citas/pasadas", "/paciente/citas/reprogramar", "/paciente/citas/reprogramar/2",
        "/paciente/avisos", "/paciente/whatsapp"};

    @Autowired
    MockMvc mvc;

    @Test
    void elPacienteVeTodas() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).with(user("p@prueba.pe").roles("PACIENTE"))).andExpect(status().isOk());
        }
    }

    @Test
    void lasPublicasNoPidenSesion() throws Exception {
        for (String ruta : new String[] {"/", "/login", "/registro", "/recuperar", "/recuperar/cambiar"}) {
            mvc.perform(get(ruta)).andExpect(status().isOk());
        }
    }
}
