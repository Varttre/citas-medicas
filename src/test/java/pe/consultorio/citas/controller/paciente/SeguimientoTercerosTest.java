package pe.consultorio.citas.controller.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.IndicacionComando;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.PacienteService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/**
La cita indicada siempre a la vista (paciente y médico) y agendada por el propio paciente; «Mis terceros» y elegir un
tercero que la cuenta ya tiene al agendar para otra persona.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class SeguimientoTercerosTest {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaIndicadaService indicadas;
    @Autowired PacienteService pacienteService;
    @Autowired TipoConsultaRepository tipos;
    @Autowired MedicoTipoConsultaRepository medicoTipos;
    @Autowired PacienteRepository pacientes;
    @Autowired JdbcTemplate jdbc;

    Especialidad especialidad;
    Medico lucero;
    Medico quispe;
    TipoConsulta control;
    Cuenta cuenta;
    UsuarioSesion usuario;
    LocalDate dia = LocalDate.now().plusDays(3);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Endocrinología");
        lucero = datos.crearMedico("Lucero", especialidad);
        quispe = datos.crearMedico("Quispe", especialidad);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(lucero, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(12, 0));
            datos.crearHorario(quispe, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(12, 0));
        }
        // Un tipo que no se reserva por la web y que solo atiende Lucero: la indicación lo habilita igual.
        control = new TipoConsulta();
        control.setNombre("Control indicado " + System.nanoTime());
        control.setDuracionDefecto((short) 30);
        control.setAjusteMedico(true);
        control.setAjusteRecepcion(true);
        control.setReservableWeb(false);
        control.setModalidadPermitida(ModalidadPermitida.AMBAS);
        control.setActivo(true);
        control = tipos.save(control);
        MedicoTipoConsulta mt = new MedicoTipoConsulta();
        mt.setMedico(lucero);
        mt.setTipoConsulta(control);
        mt.setDuracionConfigurada((short) 30);
        medicoTipos.save(mt);

        cuenta = datos.crearCuentaPaciente("seguimiento" + System.nanoTime() + "@prueba.pe");
        usuario = detalles.loadUserByUsername(cuenta.getCorreo());
    }

    /** Lucero atendió al paciente hace dos días y le indicó un control desde mañana. */
    private CitaIndicada indicarA(Paciente p) {
        Cita origen = datos.crearCita(lucero, p, LocalDateTime.now().minusDays(2).withHour(9).withMinute(0)
            .withSecond(0).withNano(0), 30, EstadoCita.REALIZADA);
        return indicadas.indicar(origen.getIdCita(), new IndicacionComando(control.getIdTipoConsulta(), 30,
            LocalDate.now().plusDays(1), null, "Traer análisis de glucosa"), null);
    }

    private static Cookie sesion(org.springframework.test.web.servlet.ResultActions r) {
        return r.andReturn().getResponse().getCookie("SESSION");
    }

    private String hora(LocalDateTime t) {
        return t.format(T);
    }

    /** PA-148 */
    @Test
    void laCitaIndicadaSeVeSiempreYElPacienteLaAgendaDesdeAgendarSeguimiento() throws Exception {
        Paciente propio = cuenta.getPacientePropio();
        Long id = indicarA(propio).getIdIndicacion();

        for (String pantalla : new String[] {"/paciente/inicio", "/paciente/citas"}) {
            mvc.perform(get(pantalla).with(user(usuario)))
                .andExpect(content().string(containsString("Citas indicadas por tu médico")))
                .andExpect(content().string(containsString("Traer análisis de glucosa")))
                .andExpect(content().string(containsString("/paciente/cita/nueva/seguimiento/" + id)));
        }

        Cookie s = sesion(mvc.perform(get("/paciente/cita/nueva/seguimiento/" + id).with(user(usuario)))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2")));
        // Solo los médicos que atienden el tipo indicado.
        mvc.perform(get("/paciente/cita/nueva/2").with(user(usuario)).cookie(s)
                .param("especialidad", especialidad.getIdEspecialidad().toString()))
            .andExpect(content().string(containsString("Cita indicada para")))
            .andExpect(content().string(containsString("Lucero Prueba")))
            .andExpect(content().string(not(containsString("Quispe Prueba"))));
        String horarios = "/paciente/cita/nueva/4?medico=" + lucero.getIdMedico() + "&especialidad="
            + especialidad.getIdEspecialidad();
        mvc.perform(get("/paciente/cita/nueva/3").with(user(usuario)).cookie(s)
                .param("medico", lucero.getIdMedico().toString())
                .param("especialidad", especialidad.getIdEspecialidad().toString()))
            .andExpect(redirectedUrl(horarios + "&indicacion=" + id));
        mvc.perform(get("/paciente/cita/nueva/4").with(user(usuario)).cookie(s)
                .param("medico", lucero.getIdMedico().toString())
                .param("especialidad", especialidad.getIdEspecialidad().toString()).param("indicacion", id.toString()))
            .andExpect(content().string(containsString(control.getNombre())));
        mvc.perform(post("/paciente/cita/nueva/4").with(user(usuario)).with(csrf()).cookie(s)
                .param("inicio", hora(dia.atTime(9, 0))))
            .andExpect(redirectedUrl("/paciente/cita/nueva/5"));
        mvc.perform(post("/paciente/cita/nueva/5").with(user(usuario)).with(csrf()).cookie(s)
                .param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/paciente/citas"))
            .andExpect(flash().attribute("exito", containsString("Cita indicada agendada")));

        Map<String, Object> ind = jdbc.queryForMap(
            "select estado, id_cita_generada from cita_indicada where id_indicacion = ?", id);
        assertThat(ind.get("estado")).isEqualTo("AGENDADA");
        Map<String, Object> cita = jdbc.queryForMap(
            "select id_paciente, id_tipo_consulta, canal_origen, inicio from cita where id_cita = ?",
            ind.get("id_cita_generada"));
        assertThat(((Number) cita.get("id_paciente")).longValue()).isEqualTo(propio.getIdPaciente());
        assertThat(((Number) cita.get("id_tipo_consulta")).shortValue()).isEqualTo(control.getIdTipoConsulta());
        assertThat(cita.get("canal_origen")).isEqualTo("WEB");
        mvc.perform(get("/paciente/inicio").with(user(usuario)))
            .andExpect(content().string(not(containsString("/paciente/cita/nueva/seguimiento/" + id))));
    }

    /** PA-149 */
    @Test
    void elPaso3SeOfreceSoloSiElMedicoAtiendeLaIndicacionYOtraCuentaNoPuedeUsarla() throws Exception {
        Long id = indicarA(cuenta.getPacientePropio()).getIdIndicacion();
        Cookie s = sesion(mvc.perform(get("/paciente/cita/nueva").with(user(usuario))));
        mvc.perform(post("/paciente/cita/nueva").with(user(usuario)).with(csrf()).cookie(s).param("paraQuien", "MI"))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2"));
        mvc.perform(get("/paciente/cita/nueva/3").with(user(usuario)).cookie(s)
                .param("medico", lucero.getIdMedico().toString())
                .param("especialidad", especialidad.getIdEspecialidad().toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Agendar la cita indicada")))
            .andExpect(content().string(containsString("Agendar una consulta nueva")))
            .andExpect(content().string(containsString("Traer análisis de glucosa")));
        mvc.perform(get("/paciente/cita/nueva/3").with(user(usuario)).cookie(s)
                .param("medico", quispe.getIdMedico().toString())
                .param("especialidad", especialidad.getIdEspecialidad().toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva/4?medico=" + quispe.getIdMedico() + "&especialidad="
                + especialidad.getIdEspecialidad()));

        // «Consulta nueva» con Lucero: se agenda el tipo web y la indicación sigue pendiente.
        mvc.perform(get("/paciente/cita/nueva/4").with(user(usuario)).cookie(s)
            .param("medico", lucero.getIdMedico().toString())
            .param("especialidad", especialidad.getIdEspecialidad().toString()));
        mvc.perform(post("/paciente/cita/nueva/4").with(user(usuario)).with(csrf()).cookie(s)
            .param("inicio", hora(dia.atTime(10, 0))));
        mvc.perform(post("/paciente/cita/nueva/5").with(user(usuario)).with(csrf()).cookie(s)
                .param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/paciente/citas"));
        assertThat(jdbc.queryForObject("select estado from cita_indicada where id_indicacion = ?", String.class, id))
            .isEqualTo("PENDIENTE");

        // Otra cuenta no ve ni puede agendar esa indicación.
        Cuenta otra = datos.crearCuentaPaciente("ajena" + System.nanoTime() + "@prueba.pe");
        UsuarioSesion ajeno = detalles.loadUserByUsername(otra.getCorreo());
        mvc.perform(get("/paciente/cita/nueva/seguimiento/" + id).with(user(ajeno)))
            .andExpect(redirectedUrl("/paciente/inicio"))
            .andExpect(flash().attribute("error", containsString("ya no está pendiente")));
        Cookie s2 = sesion(mvc.perform(get("/paciente/cita/nueva").with(user(ajeno))));
        mvc.perform(get("/paciente/cita/nueva/4").with(user(ajeno)).cookie(s2)
                .param("medico", lucero.getIdMedico().toString())
                .param("especialidad", especialidad.getIdEspecialidad().toString()).param("indicacion", id.toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2"))
            .andExpect(flash().attribute("error", containsString("ya no está pendiente")));
        mvc.perform(get("/paciente/inicio").with(user(ajeno)))
            .andExpect(content().string(not(containsString("Traer análisis de glucosa"))));
    }

    /** PA-150 */
    @Test
    void misTercerosAnadeYAgendaParaUnTerceroYaGuardadoYNoParaUnoRevocado() throws Exception {
        String documento = String.valueOf(74000000 + System.nanoTime() % 999999);
        mvc.perform(post("/paciente/terceros").with(user(usuario)).with(csrf())
                .param("tipoDocumento", "DNI").param("nroDocumento", documento).param("nombres", "Valeria")
                .param("apellidos", "Soto Paz").param("fechaNacimiento", "2016-03-10").param("consentimiento", "true"))
            .andExpect(redirectedUrl("/paciente/terceros"))
            .andExpect(flash().attribute("exito", containsString("ya está entre tus terceros")));
        Paciente tercero = pacientes.findAll().stream().filter(p -> p.getNroDocumento().equals(documento))
            .findFirst().orElseThrow();
        mvc.perform(get("/paciente/terceros").with(user(usuario)))
            .andExpect(content().string(containsString("Valeria Soto Paz")))
            .andExpect(content().string(containsString("/paciente/cita/nueva?tercero=" + tercero.getIdPaciente())));

        // Desde «Mis terceros» llega ya elegido en el paso 1.
        Cookie s = sesion(mvc.perform(get("/paciente/cita/nueva").with(user(usuario))
                .param("tercero", tercero.getIdPaciente().toString()))
            .andExpect(content().string(containsString("¿Para cuál de tus terceros?")))
            .andExpect(content().string(matchesPattern("(?s).*value=\"" + tercero.getIdPaciente()
                + "\"\\s+data-oculta=\"#datos-nuevo\" checked=\"checked\".*"))));
        mvc.perform(post("/paciente/cita/nueva").with(user(usuario)).with(csrf()).cookie(s)
                .param("paraQuien", "OTRA").param("idTercero", tercero.getIdPaciente().toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2"));
        mvc.perform(get("/paciente/cita/nueva/4").with(user(usuario)).cookie(s)
            .param("medico", quispe.getIdMedico().toString())
            .param("especialidad", especialidad.getIdEspecialidad().toString()));
        mvc.perform(post("/paciente/cita/nueva/4").with(user(usuario)).with(csrf()).cookie(s)
            .param("inicio", hora(dia.atTime(11, 0))));
        mvc.perform(get("/paciente/cita/nueva/5").with(user(usuario)).cookie(s))
            .andExpect(content().string(containsString("Valeria Soto Paz")));
        mvc.perform(post("/paciente/cita/nueva/5").with(user(usuario)).with(csrf()).cookie(s)
                .param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/paciente/citas"));
        Map<String, Object> cita = jdbc.queryForMap(
            "select id_cuenta_titular from cita where id_paciente = ?", tercero.getIdPaciente());
        assertThat(((Number) cita.get("id_cuenta_titular")).longValue()).isEqualTo(cuenta.getIdCuenta());

        // Si el paciente retira su consentimiento, ya no se puede elegir ni aparece.
        pacienteService.revocarCuenta(tercero.getIdPaciente(), cuenta.getIdCuenta());
        mvc.perform(post("/paciente/cita/nueva").with(user(usuario)).with(csrf())
                .param("paraQuien", "OTRA").param("idTercero", tercero.getIdPaciente().toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva"))
            .andExpect(flash().attribute("error", containsString("ya no está entre tus terceros")));
        mvc.perform(get("/paciente/terceros").with(user(usuario)))
            .andExpect(content().string(not(containsString("Valeria Soto Paz"))));
    }

    /** PA-151 */
    @Test
    void elMedicoVeEnSuAgendaLaCitaIndicadaPendienteDelPacienteAunqueLaHayaDadoOtro() throws Exception {
        Paciente propio = cuenta.getPacientePropio();
        indicarA(propio);
        datos.crearCita(quispe, propio, dia.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        UsuarioSesion medico = detalles.loadUserByUsername(quispe.getCuenta().getCorreo());
        mvc.perform(get("/medico/agenda").with(user(medico)).param("fecha", dia.toString()))
            .andExpect(content().string(containsString("Cita indicada pendiente")))
            .andExpect(content().string(containsString("indicada por Lucero Prueba")))
            .andExpect(content().string(containsString("Traer análisis de glucosa")));
    }
}
