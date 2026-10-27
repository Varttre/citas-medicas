package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.PacienteRepository;

/** CRUD de pacientes en recepción contra la base real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PacientesCrudTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PacienteRepository pacientes;

    private MockHttpServletRequestBuilder formulario(String url, String documento, String telefono) {
        return post(url).with(user("r@prueba.pe").roles("RECEPCION")).with(csrf())
            .param("tipoDocumento", "DNI").param("nroDocumento", documento)
            .param("nombres", "Rosa").param("apellidos", "Medina Flores")
            .param("fechaNacimiento", "1958-05-14").param("telefono", telefono);
    }

    @Test
    void registraBuscaEditaYRechazaDuplicados() throws Exception {
        mvc.perform(formulario("/recepcion/pacientes", "10456789", "944870112"))
            .andExpect(status().is3xxRedirection());
        Paciente p = pacientes.findAll().stream()
            .filter(x -> x.getTipoDocumento() == TipoDocumento.DNI && x.getNroDocumento().equals("10456789"))
            .findFirst().orElseThrow();

        mvc.perform(get("/recepcion/pacientes").param("q", "medina").with(user("r@prueba.pe").roles("RECEPCION")))
            .andExpect(content().string(containsString("Rosa Medina Flores")));
        mvc.perform(get("/recepcion/pacientes/" + p.getIdPaciente()).with(user("r@prueba.pe").roles("RECEPCION")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("944870112")));

        mvc.perform(formulario("/recepcion/pacientes/" + p.getIdPaciente(), "10456789", "900111222"))
            .andExpect(status().is3xxRedirection());
        assertThat(pacientes.findById(p.getIdPaciente()).orElseThrow().getTelefono()).isEqualTo("900111222");

        mvc.perform(formulario("/recepcion/pacientes", "10456789", "944870112"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ya existe un paciente con ese documento.")));
    }

    @Test
    void validaLosCamposObligatorios() throws Exception {
        mvc.perform(post("/recepcion/pacientes").with(user("r@prueba.pe").roles("RECEPCION")).with(csrf())
                .param("tipoDocumento", "DNI").param("nroDocumento", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ingresa el número de documento.")));
    }
}
