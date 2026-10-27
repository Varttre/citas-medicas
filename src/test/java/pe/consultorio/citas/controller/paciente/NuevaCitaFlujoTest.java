package pe.consultorio.citas.controller.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;
import pe.consultorio.citas.service.disponibilidad.OfertaDia;

/** El paciente agenda una cita de punta a punta: médico, horario retenido, confirmación y aparece en Mis citas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class NuevaCitaFlujoTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    UsuarioDetallesService detalles;

    @Autowired
    DisponibilidadService disponibilidad;

    @Test
    void agendaUnaCitaYLaVeEnMisCitas() throws Exception {
        Especialidad e = datos.crearEspecialidad("Oftalmología");
        Medico m = datos.crearMedico("Flujo", e);
        for (int dia = 1; dia <= 7; dia++) {
            datos.crearHorario(m, LocalDate.now().minusDays(1), dia, LocalTime.of(8, 0), LocalTime.of(12, 0));
        }
        Cuenta c = datos.crearCuentaPaciente("flujo" + System.nanoTime() + "@prueba.pe");
        UsuarioSesion paciente = detalles.loadUserByUsername(c.getCorreo());
        mvc.perform(get("/paciente/cita/nueva/2").param("especialidad", e.getIdEspecialidad().toString())
                .with(user(paciente)))
            .andExpect(content().string(containsString("Flujo Prueba")));
        // La sesión vive en la base (Spring Session JDBC): se sigue con la cookie SESSION, como un navegador.
        Cookie sesion = mvc.perform(get("/paciente/cita/nueva/4").param("medico", m.getIdMedico().toString())
                .param("especialidad", e.getIdEspecialidad().toString()).with(user(paciente)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getCookie("SESSION");
        assertThat(sesion).isNotNull();

        OfertaDia dia = disponibilidad.ofertasProximas(m.getIdMedico(), 30, LocalDate.now().plusDays(1), null).get(0);
        LocalDateTime inicio = dia.ofertas().get(0).inicio();
        mvc.perform(post("/paciente/cita/nueva/4").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("inicio", inicio.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))))
            .andExpect(redirectedUrl("/paciente/cita/nueva/5"));
        mvc.perform(get("/paciente/cita/nueva/5").with(user(paciente)).cookie(sesion))
            .andExpect(content().string(containsString("reservado para ti")));
        mvc.perform(post("/paciente/cita/nueva/5").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/paciente/citas"));

        String misCitas = mvc.perform(get("/paciente/citas").with(user(paciente)))
            .andReturn().getResponse().getContentAsString();
        assertThat(misCitas).contains("Oftalmología").contains("Flujo Prueba");
    }
}
