package pe.consultorio.citas.controller.medico;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** Las pantallas del médico cargan con su sesión, y «Mi horario» muestra y guarda datos reales. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PantallasMedicoTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    UsuarioDetallesService detalles;

    UsuarioSesion medico;

    @BeforeEach
    void preparar() {
        Medico m = datos.crearMedico("Rosa", datos.crearEspecialidad("Neurología"));
        datos.crearHorario(m, LocalDate.now().minusDays(1), 2, LocalTime.of(8, 0), LocalTime.of(12, 0));
        medico = detalles.loadUserByUsername(m.getCuenta().getCorreo());
    }

    @Test
    void lasPantallasCargan() throws Exception {
        for (String ruta : new String[] {"/medico/inicio", "/medico/agenda", "/medico/estadisticas"}) {
            mvc.perform(get(ruta).with(user(medico))).andExpect(status().isOk());
        }
        mvc.perform(get("/medico/horario").with(user(medico)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("08:00–12:00")));
    }

    @Test
    void registraUnaExcepcion() throws Exception {
        String dia = LocalDate.now().plusDays(60).toString();
        mvc.perform(post("/medico/horario/excepciones").with(user(medico)).with(csrf())
                .param("desde", dia).param("hasta", dia).param("motivo", "Congreso"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/medico/horario").with(user(medico)))
            .andExpect(content().string(containsString("Congreso")));
    }
}
