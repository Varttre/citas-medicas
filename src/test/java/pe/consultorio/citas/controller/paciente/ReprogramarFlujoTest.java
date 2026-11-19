package pe.consultorio.citas.controller.paciente;

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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import jakarta.servlet.http.Cookie;
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
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** El paciente reprograma desde «Mis citas»: misma cita, mismo id, sin perder lo elegido si el horario se ocupa. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class ReprogramarFlujoTest {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;
    @Autowired CitaEventoRepository eventos;
    @Autowired PreferenciaService preferencias;
    @Autowired PreferenciaDisponibilidadRepository preferenciasRepo;

    Medico medico;
    Cuenta cuenta;
    UsuarioSesion paciente;
    LocalDate dia = LocalDate.now().plusDays(2);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Reprograma", datos.crearEspecialidad("Nefrología"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(12, 0));
        }
        cuenta = datos.crearCuentaPaciente("reprograma" + System.nanoTime() + "@prueba.pe");
        paciente = detalles.loadUserByUsername(cuenta.getCorreo());
    }

    private Cita citaPropia(LocalDateTime inicio, EstadoCita estado) {
        return datos.crearCita(medico, cuenta.getPacientePropio(), inicio, 30, estado);
    }

    /** Paso 1 → elegir horario → devuelve la cookie de sesión para seguir el flujo. */
    private Cookie elegir(Cita c, LocalDateTime nuevo) throws Exception {
        Cookie sesion = mvc.perform(get("/paciente/citas/" + c.getIdCita() + "/reprogramar").with(user(paciente)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Reprograma Prueba")))
            .andReturn().getResponse().getCookie("SESSION");
        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/reprogramar/bloqueo").with(user(paciente))
                .with(csrf()).cookie(sesion).param("nuevoInicio", nuevo.format(T)))
            .andExpect(redirectedUrl("/paciente/citas/" + c.getIdCita() + "/reprogramar/confirmar"));
        return sesion;
    }

    @Test
    void unaCitaConfirmadaSeReprogramaConservandoIdYAntiguedadEnLaLista() throws Exception {
        Cita c = citaPropia(dia.atTime(9, 0), EstadoCita.CONFIRMADA);
        preferencias.guardar(c.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 60, 2), null);
        LocalDateTime inscrito = preferenciasRepo.findByCitaIdCita(c.getIdCita()).orElseThrow().getInscritoEn();

        Cookie sesion = elegir(c, dia.atTime(11, 0));
        mvc.perform(get("/paciente/citas/" + c.getIdCita() + "/reprogramar/confirmar").with(user(paciente))
                .cookie(sesion))
            .andExpect(content().string(containsString("reservado para ti")))
            .andExpect(content().string(containsString("Sigo aceptando")));
        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/reprogramar").with(user(paciente)).with(csrf())
                .cookie(sesion).param("aceptaLlamada", "true").param("alcance", "MEDICO")
                .param("minLlegada", "45").param("diasPrevios", "2"))
            .andExpect(redirectedUrl("/paciente/citas"))
            .andExpect(flash().attribute("exito", containsString("Cita reprogramada")));

        Cita r = citas.findById(c.getIdCita()).orElseThrow();
        assertThat(r.getInicio()).isEqualTo(dia.atTime(11, 0));
        assertThat(r.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(eventos.findAll()).anyMatch(e -> e.getCita().getIdCita().equals(c.getIdCita())
            && e.getTipoEvento() == TipoEvento.REPROGRAMACION);
        PreferenciaDisponibilidad p = preferenciasRepo.findByCitaIdCita(c.getIdCita()).orElseThrow();
        assertThat(p.getMinLlegadaMin()).isEqualTo((short) 45);
        assertThat(p.getInscritoEn()).isEqualTo(inscrito);
    }

    @Test
    void siOtroTomaElHorarioVuelveAlPaso1YLaCitaQuedaIgual() throws Exception {
        Cita c = citaPropia(dia.atTime(8, 0), EstadoCita.PROGRAMADA);
        Cookie sesion = elegir(c, dia.atTime(10, 30));
        datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            dia.atTime(10, 30), 30, EstadoCita.PROGRAMADA);

        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/reprogramar").with(user(paciente)).with(csrf())
                .cookie(sesion))
            .andExpect(redirectedUrl("/paciente/citas/" + c.getIdCita() + "/reprogramar"))
            .andExpect(flash().attribute("error", containsString("acaba de ser ocupado")));
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getInicio()).isEqualTo(dia.atTime(8, 0));
    }

    @Test
    void unaCuentaQueNoVeLaCitaRecibe404() throws Exception {
        Cita c = citaPropia(dia.atTime(9, 30), EstadoCita.PROGRAMADA);
        Cuenta otra = datos.crearCuentaPaciente("ajena" + System.nanoTime() + "@prueba.pe");
        mvc.perform(get("/paciente/citas/" + c.getIdCita() + "/reprogramar")
                .with(user(detalles.loadUserByUsername(otra.getCorreo()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void unaCitaYaIniciadaNoMuestraElBotonYSeRechaza() throws Exception {
        LocalDateTime hace15 = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            .plusMinutes((LocalDateTime.now().getMinute() / 15) * 15L).minusMinutes(15);
        Cita c = citaPropia(hace15, EstadoCita.PROGRAMADA);
        mvc.perform(get("/paciente/citas").with(user(paciente)))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                containsString("/paciente/citas/" + c.getIdCita() + "/reprogramar"))));
        mvc.perform(get("/paciente/citas/" + c.getIdCita() + "/reprogramar").with(user(paciente)))
            .andExpect(redirectedUrl("/paciente/citas"))
            .andExpect(flash().attribute("error", "Esta cita ya no se puede reprogramar."));
    }
}
