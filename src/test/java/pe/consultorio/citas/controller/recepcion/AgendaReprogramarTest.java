package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** Recepción reprograma desde la agenda conservando el id, y ajusta la duración si el tipo lo permite. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AgendaReprogramarTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;

    Medico medico;
    UsuarioSesion recepcion;
    LocalDate dia = LocalDate.now().plusDays(3);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Agenda", datos.crearEspecialidad("Hematología"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(14, 0), LocalTime.of(18, 0));
        }
        String correo = "recepagenda" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción", null);
        recepcion = detalles.loadUserByUsername(correo);
    }

    private Cita cita(LocalTime hora) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            dia.atTime(hora), 30, EstadoCita.CONFIRMADA);
    }

    @Test
    void reprogramaDesdeLaAgendaConservandoElId() throws Exception {
        Cita c = cita(LocalTime.of(14, 0));
        mvc.perform(get("/recepcion/citas/" + c.getIdCita() + "/reprogramar").with(user(recepcion)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Horarios libres")))
            .andExpect(content().string(containsString("16:00")));
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/reprogramar").with(user(recepcion)).with(csrf())
                .param("nuevoInicio", dia + "T16:00"))
            .andExpect(redirectedUrl("/recepcion/agenda?fecha=" + dia))
            .andExpect(flash().attribute("exito", containsString("Cita reprogramada")));
        Cita r = citas.findById(c.getIdCita()).orElseThrow();
        assertThat(r.getInicio()).isEqualTo(dia.atTime(16, 0));
        assertThat(r.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void unHorarioOcupadoVuelveALaListaConElMensaje() throws Exception {
        Cita c = cita(LocalTime.of(14, 30));
        cita(LocalTime.of(17, 0));
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/reprogramar").with(user(recepcion)).with(csrf())
                .param("nuevoInicio", dia + "T17:00"))
            .andExpect(redirectedUrl("/recepcion/citas/" + c.getIdCita() + "/reprogramar?fecha=" + dia))
            .andExpect(flash().attribute("error", containsString("ocupado")));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getInicio()).isEqualTo(dia.atTime(14, 30));
    }

    @Test
    void ajustaLaDuracionYRechazaLaQueSeCruza() throws Exception {
        Cita c = cita(LocalTime.of(15, 0));
        cita(LocalTime.of(16, 0));
        mvc.perform(get("/recepcion/agenda").param("fecha", dia.toString()).with(user(recepcion)))
            .andExpect(content().string(containsString("Ajustar")));
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/duracion").with(user(recepcion)).with(csrf())
                .param("nuevaDuracion", "60").param("fecha", dia.toString()))
            .andExpect(flash().attribute("exito", "Duración cambiada a 60 minutos."));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getFin()).isEqualTo(dia.atTime(16, 0));

        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/duracion").with(user(recepcion)).with(csrf())
                .param("nuevaDuracion", "75").param("fecha", dia.toString()))
            .andExpect(flash().attribute("error", "La nueva duración se cruza con la siguiente cita."));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getFin()).isEqualTo(dia.atTime(16, 0));
    }
}
