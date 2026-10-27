package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** Recepción registra la llegada y el médico atiende desde su agenda; un médico no toca citas de otro. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AgendasTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    UsuarioService usuarios;

    @Autowired
    UsuarioDetallesService detalles;

    @Autowired
    CitaRepository citas;

    @Test
    void llegadaAtencionYAislamientoEntreMedicos() throws Exception {
        Especialidad e = datos.crearEspecialidad("Endocrinología");
        Medico m = datos.crearMedico("Agenda", e);
        Medico otro = datos.crearMedico("Ajeno", e);
        LocalDateTime inicio = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            .plusMinutes((LocalDateTime.now().getMinute() / 15 + 1) * 15L);
        Cita c = datos.crearCita(m, datos.crearPaciente("84000001"), inicio, 30, EstadoCita.PROGRAMADA);

        String correo = "recep" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción de prueba", null);
        UsuarioSesion recepcion = detalles.loadUserByUsername(correo);
        UsuarioSesion medico = detalles.loadUserByUsername(m.getCuenta().getCorreo());
        UsuarioSesion medicoAjeno = detalles.loadUserByUsername(otro.getCuenta().getCorreo());

        mvc.perform(get("/recepcion/agenda").param("fecha", inicio.toLocalDate().toString()).with(user(recepcion)))
            .andExpect(content().string(containsString("Agenda Prueba")));
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/llegada").with(user(recepcion)).with(csrf()))
            .andExpect(status().is3xxRedirection());

        mvc.perform(post("/medico/citas/" + c.getIdCita() + "/iniciar").with(user(medicoAjeno)).with(csrf()))
            .andExpect(status().isNotFound());

        mvc.perform(post("/medico/citas/" + c.getIdCita() + "/iniciar").with(user(medico)).with(csrf()));
        mvc.perform(post("/medico/citas/" + c.getIdCita() + "/finalizar").with(user(medico)).with(csrf()));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.REALIZADA);
    }
}
