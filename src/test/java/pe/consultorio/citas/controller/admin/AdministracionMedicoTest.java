package pe.consultorio.citas.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.CitaAgenda;
import pe.consultorio.citas.dto.EspecialidadForm;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.service.AgendaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.HorarioService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/**
Bloque 4: el médico se crea con su horario, la agenda se consulta por rango de días y por paciente con filtros, y una
especialidad con médicos activos no se desactiva.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AdministracionMedicoTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarioService;
    @Autowired UsuarioDetallesService detalles;
    @Autowired HorarioService horarios;
    @Autowired AgendaService agenda;
    @Autowired ConfiguracionService configuracion;
    @Autowired CuentaRepository cuentas;
    @Autowired MedicoRepository medicos;
    @Autowired DatosPrueba datos;

    UsuarioSesion admin;
    UsuarioSesion recepcion;

    @BeforeEach
    void preparar() {
        String a = "admin4-" + System.nanoTime() + "@prueba.pe";
        usuarioService.crearPersonal(a, "Clave2026!", NombreRol.ADMINISTRADOR, "Admin bloque 4", null);
        admin = detalles.loadUserByUsername(a);
        String r = "recep4-" + System.nanoTime() + "@prueba.pe";
        usuarioService.crearPersonal(r, "Clave2026!", NombreRol.RECEPCION, "Recepción bloque 4", null);
        recepcion = detalles.loadUserByUsername(r);
    }

    private MockHttpServletRequestBuilder altaMedico(String correo, Especialidad e) {
        MockHttpServletRequestBuilder req = post("/admin/usuarios/medicos").with(user(admin)).with(csrf())
            .param("correo", correo).param("contrasena", "Clave2026!").param("nombres", "Lucía")
            .param("apellidos", "Horario").param("idEspecialidad", e.getIdEspecialidad().toString());
        for (TipoConsulta t : configuracion.tiposActivos()) {
            req.param("dur_" + t.getIdTipoConsulta(), "30");
        }
        return req;
    }

    private Paciente paciente() {
        return datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000));
    }

    /** PA-158 ★ */
    @Test
    void elMedicoSeCreaConSuHorarioDesdeAdministracion() throws Exception {
        datos.crearMedico("Tipos", datos.crearEspecialidad("Asegura tipos"));
        Especialidad e = datos.crearEspecialidad("Alta con horario");
        String correo = "conhorario" + System.nanoTime() + "@prueba.pe";
        mvc.perform(altaMedico(correo, e)
                .param("dia_1", "1").param("ini_1_1", "08:00").param("fin_1_1", "12:00")
                .param("ini_1_2", "15:00").param("fin_1_2", "18:00")
                .param("dia_3", "1").param("ini_3_1", "09:00").param("fin_3_1", "13:00")
                .param("ini_5_1", "09:00").param("fin_5_1", "13:00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("exito", containsString("con su horario")));

        Medico m = medicos.findByCuentaIdCuenta(cuentas.findByCorreo(correo).orElseThrow().getIdCuenta())
            .orElseThrow();
        var datosHorario = horarios.verHorario(m.getIdMedico());
        assertThat(datosHorario.versiones()).singleElement().satisfies(v -> {
            assertThat(v.vigenteDesde()).isEqualTo(LocalDate.now());
            // El viernes no se marcó: sus horas se ignoran.
            assertThat(v.franjas()).containsExactly(
                new FranjaDatos(1, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new FranjaDatos(1, LocalTime.of(15, 0), LocalTime.of(18, 0)),
                new FranjaDatos(3, LocalTime.of(9, 0), LocalTime.of(13, 0)));
        });
    }

    /** PA-159 */
    @Test
    void sinHorarioOConUnHorarioInvalidoNoSeCreaNadaYElFormularioConservaLoEscrito() throws Exception {
        datos.crearMedico("Tipos", datos.crearEspecialidad("Asegura tipos"));
        Especialidad e = datos.crearEspecialidad("Alta inválida");
        String sinDias = "sindias" + System.nanoTime() + "@prueba.pe";
        mvc.perform(altaMedico(sinDias, e))
            .andExpect(flash().attribute("errorAlta", containsString("al menos un día")))
            .andExpect(flash().attribute("campoError", "horario"));
        assertThat(cuentas.findByCorreo(sinDias)).isEmpty();

        String alReves = "alreves" + System.nanoTime() + "@prueba.pe";
        var resultado = mvc.perform(altaMedico(alReves, e)
                .param("dia_2", "1").param("ini_2_1", "12:00").param("fin_2_1", "08:00"))
            .andExpect(flash().attribute("errorAlta", containsString("posterior")))
            .andExpect(flash().attribute("campoError", "horario"))
            .andReturn();
        // La cuenta, el médico y sus duraciones se deshacen junto con el horario.
        assertThat(cuentas.findByCorreo(alReves)).isEmpty();
        // Lo escrito vuelve al formulario (menos la contraseña), y la pantalla lo dibuja abierto y con lo enviado.
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> enviado =
            (java.util.Map<String, String>) resultado.getFlashMap().get("altaMedico");
        assertThat(enviado).containsEntry("correo", alReves).containsEntry("ini_2_1", "12:00")
            .doesNotContainKey("contrasena");
        // La sesión vive en la base (Spring Session JDBC): como un navegador, se vuelve con la cookie de sesión.
        mvc.perform(get("/admin/usuarios").with(user(admin)).cookie(resultado.getResponse().getCookies()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"" + alReves + "\"")))
            .andExpect(content().string(containsString("Corrige el campo marcado")))
            .andExpect(content().string(containsString("Offcanvas.getOrCreateInstance")));

        // Un correo repetido se marca en su campo.
        mvc.perform(altaMedico(admin.getUsername(), e).param("dia_1", "1").param("ini_1_1", "08:00")
                .param("fin_1_1", "12:00"))
            .andExpect(flash().attribute("campoError", "correo"));
    }

    /** PA-160 ★ */
    @Test
    void laAgendaSeConsultaPorRangoPorPacienteYPorEstado() {
        Medico m = datos.crearMedico("Rango", datos.crearEspecialidad("Rango"));
        Paciente rosa = paciente();
        LocalDate lunes = LocalDate.of(2025, 6, 2);
        datos.crearCita(m, rosa, lunes.atTime(9, 0), 30, EstadoCita.REALIZADA);
        datos.crearCita(m, paciente(), lunes.plusDays(2).atTime(10, 0), 30, EstadoCita.CANCELADA_POR_PACIENTE);
        datos.crearCita(m, rosa, lunes.plusDays(40).atTime(11, 0), 30, EstadoCita.PROGRAMADA);

        List<CitaAgenda> rango = agenda.buscar(lunes, lunes.plusDays(6), m.getIdMedico(), null, null, null);
        assertThat(rango).extracting(CitaAgenda::inicio)
            .containsExactly(lunes.atTime(9, 0), lunes.plusDays(2).atTime(10, 0));

        List<CitaAgenda> canceladas = agenda.buscar(lunes, lunes.plusDays(6), m.getIdMedico(), null, "CANCELADAS",
            null);
        assertThat(canceladas).singleElement().satisfies(c -> assertThat(c.estadoCodigo())
            .isEqualTo("CANCELADA_POR_PACIENTE"));

        // Por paciente: todas sus citas, en cualquier fecha, y solo las suyas.
        List<CitaAgenda> deRosa = agenda.buscar(null, null, m.getIdMedico(), null, null, rosa.getNroDocumento());
        assertThat(deRosa).hasSize(2).allSatisfy(c -> assertThat(c.documento()).endsWith(rosa.getNroDocumento()));

        assertThatThrownBy(() -> agenda.buscar(lunes, lunes.plusDays(70), null, null, null, null))
            .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("62 días");
        assertThatThrownBy(() -> agenda.buscar(lunes, lunes.minusDays(1), null, null, null, null))
            .isInstanceOf(ReglaNegocioException.class);
    }

    /** PA-161 */
    @Test
    void laAgendaDeRecepcionMuestraElRangoYVuelveConLosMismosFiltros() throws Exception {
        Medico m = datos.crearMedico("Vuelta", datos.crearEspecialidad("Vuelta"));
        LocalDate hoy = LocalDate.now();
        Cita c = datos.crearCita(m, paciente(), hoy.atTime(23, 30), 15, EstadoCita.PROGRAMADA);

        mvc.perform(get("/recepcion/agenda").with(user(recepcion))
                .param("fecha", hoy.toString()).param("hasta", hoy.plusDays(6).toString())
                .param("medico", m.getIdMedico().toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Agenda por rango")))
            .andExpect(content().string(containsString("Fecha y hora")))
            .andExpect(content().string(containsString("name=\"filtros\"")));

        String filtros = "fecha=" + hoy + "&hasta=" + hoy.plusDays(6) + "&medico=" + m.getIdMedico();
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/llegada").with(user(recepcion)).with(csrf())
                .param("fecha", hoy.toString()).param("filtros", filtros))
            .andExpect(redirectedUrl("/recepcion/agenda?" + filtros));
        // Algo que no es una cadena de consulta no se usa para redirigir.
        mvc.perform(post("/recepcion/citas/" + c.getIdCita() + "/llegada").with(user(recepcion)).with(csrf())
                .param("fecha", hoy.toString()).param("filtros", "//otro.sitio/x"))
            .andExpect(redirectedUrl("/recepcion/agenda?fecha=" + hoy));
    }

    /** PA-162 ★ */
    @Test
    void unaEspecialidadConMedicosActivosNoSeDesactiva() {
        Especialidad e = datos.crearEspecialidad("Con médicos");
        Medico m = datos.crearMedico("Activo", e);
        EspecialidadForm apagar = new EspecialidadForm(e.getNombre(), null, null);

        assertThatThrownBy(() -> configuracion.guardarEspecialidad(e.getIdEspecialidad(), apagar, null))
            .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("1 médico activo");

        usuarioService.darDeBaja(m.getCuenta().getIdCuenta(), null);
        configuracion.guardarEspecialidad(e.getIdEspecialidad(), apagar, null);
        assertThat(configuracion.especialidadesActivas()).extracting(Especialidad::getIdEspecialidad)
            .doesNotContain(e.getIdEspecialidad());
    }

    /** PA-163 */
    @Test
    void elMedicoBuscaPorPacienteSoloEntreSusCitas() throws Exception {
        Especialidad e = datos.crearEspecialidad("Propias");
        Medico m = datos.crearMedico("Propio", e);
        Medico otro = datos.crearMedico("Ajeno", e);
        Paciente p = paciente();
        datos.crearCita(m, p, LocalDate.of(2025, 7, 1).atTime(9, 0), 30, EstadoCita.REALIZADA);
        datos.crearCita(otro, p, LocalDate.of(2025, 7, 2).atTime(9, 0), 30, EstadoCita.REALIZADA);
        UsuarioSesion sesion = detalles.loadUserByUsername(m.getCuenta().getCorreo());

        mvc.perform(get("/medico/agenda").with(user(sesion)).param("q", p.getNroDocumento()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("01/07/2025")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("02/07/2025"))));
        mvc.perform(get("/medico/agenda").with(user(sesion)).param("fecha", "2025-07-01").param("hasta", "2025-07-31"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("· 1 cita<")));
    }
}
