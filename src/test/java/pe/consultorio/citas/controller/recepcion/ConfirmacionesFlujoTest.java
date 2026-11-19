package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** La cola de confirmaciones: confirmar saca la cita de la cola; no hay otra forma de confirmarla. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class ConfirmacionesFlujoTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;

    Medico medico;
    UsuarioSesion recepcion;

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Confirma", datos.crearEspecialidad("Dermatología"));
        String correo = "recepconf" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción", null);
        recepcion = detalles.loadUserByUsername(correo);
    }

    private Cita enLaCola(Paciente p, int horas) {
        return datos.crearCita(medico, p, LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(horas), 30,
            EstadoCita.PROGRAMADA);
    }

    @Test
    void confirmarDesdeLaColaCambiaElEstadoYLaFilaDesaparece() throws Exception {
        Paciente p = datos.crearPaciente(String.valueOf(75000000 + System.nanoTime() % 999999));
        Cita c = enLaCola(p, 20);
        mvc.perform(get("/recepcion/confirmaciones").param("medico", medico.getIdMedico().toString()).with(user(recepcion)))
            .andExpect(content().string(containsString(p.getApellidos())))
            .andExpect(content().string(containsString("Registrar resultado")));

        mvc.perform(post("/recepcion/confirmaciones/" + c.getIdCita() + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "CONFIRMO"))
            .andExpect(redirectedUrl("/recepcion/confirmaciones"))
            .andExpect(flash().attribute("exito", "Cita confirmada."));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        mvc.perform(get("/recepcion/confirmaciones").param("medico", medico.getIdMedico().toString()).with(user(recepcion)))
            .andExpect(content().string(not(containsString(p.getApellidos()))));
    }

    @Test
    void noSePuedeConfirmarDesdeLaAgendaNiCancelarSinMotivo() throws Exception {
        Cita c = enLaCola(datos.crearPaciente(String.valueOf(76000000 + System.nanoTime() % 999999)), 21);
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/estado").with(user(recepcion)).with(csrf())
                .param("nuevoEstado", "CONFIRMADA"))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/recepcion/confirmaciones/" + c.getIdCita() + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "CANCELO"))
            .andExpect(flash().attribute("error", "Indica el motivo de la cancelación."));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void reprogramoLlevaABuscarElNuevoHorario() throws Exception {
        Cita c = enLaCola(datos.crearPaciente(String.valueOf(77000000 + System.nanoTime() % 999999)), 22);
        mvc.perform(post("/recepcion/confirmaciones/" + c.getIdCita() + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "REPROGRAMO"))
            .andExpect(redirectedUrl("/recepcion/citas/" + c.getIdCita() + "/reprogramar"));
    }
}
