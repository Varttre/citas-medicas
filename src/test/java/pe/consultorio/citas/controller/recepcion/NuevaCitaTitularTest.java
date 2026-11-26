package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.TipoConsultaRepository;
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/**
Nueva cita de recepción: la cuenta titular (contacto) se elige entre las posibles del paciente, las citas indicadas
pendientes se ofrecen, y registrar a un paciente nuevo vuelve a la cita con él ya elegido.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class NuevaCitaTitularTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired RegistroService registro;
    @Autowired CitaIndicadaService indicadas;
    @Autowired TipoConsultaRepository tipos;
    @Autowired JdbcTemplate jdbc;

    Especialidad especialidad;
    Medico medico;
    TipoConsulta tipo;
    UsuarioSesion recepcion;
    LocalDate dia = LocalDate.now().plusDays(4);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Reumatología");
        medico = datos.crearMedico("Titular", especialidad);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(12, 0));
        }
        tipo = tipos.findAll().stream()
            .filter(t -> t.isActivo() && t.getModalidadPermitida() == ModalidadPermitida.AMBAS)
            .findFirst().orElseThrow();
        Cuenta c = datos.crearCuentaPersonal(NombreRol.RECEPCION);
        recepcion = detalles.loadUserByUsername(c.getCorreo());
    }

    private String doc() {
        return String.valueOf(75000000 + System.nanoTime() % 999999);
    }

    /** Agenda desde recepción con ese valor de titular y devuelve la cuenta titular que quedó en la cita (o null). */
    private Long agendarCon(Paciente p, String titular, LocalDateTime inicio) throws Exception {
        mvc.perform(post("/recepcion/cita/nueva").with(user(recepcion)).with(csrf())
                .param("idPaciente", p.getIdPaciente().toString()).param("canal", "TELEFONO")
                .param("idTipo", tipo.getIdTipoConsulta().toString()).param("modalidad", "PRESENCIAL")
                .param("idEspecialidad", especialidad.getIdEspecialidad().toString())
                .param("idMedico", medico.getIdMedico().toString()).param("inicio", inicio.toString())
                .param("duracion", String.valueOf(tipo.getDuracionDefecto())).param("titular", titular))
            .andExpect(redirectedUrl("/recepcion/agenda?fecha=" + inicio.toLocalDate()));
        Number id = jdbc.queryForObject("select id_cuenta_titular from cita where id_paciente = ? and inicio = ?",
            Number.class, p.getIdPaciente(), inicio);
        return id == null ? null : id.longValue();
    }

    /** PA-153 */
    @Test
    void laCuentaTitularSeEligeEntreLasDelPacienteYUnaAjenaSeIgnora() throws Exception {
        Paciente p = datos.crearPaciente(doc());
        Cuenta familiar = datos.crearCuentaPaciente("familiar" + System.nanoTime() + "@prueba.pe");
        registro.vincularTercero(familiar.getIdCuenta(), new TerceroForm(TipoDocumento.DNI, p.getNroDocumento(),
            p.getNombres(), p.getApellidos(), p.getFechaNacimiento(), null, true));
        Cuenta ajena = datos.crearCuentaPaciente("ajena" + System.nanoTime() + "@prueba.pe");

        mvc.perform(get("/recepcion/cita/nueva").with(user(recepcion)).param("idPaciente", p.getIdPaciente().toString()))
            .andExpect(content().string(containsString("Cuenta titular (a quién se llama por esta cita)")))
            .andExpect(content().string(containsString(familiar.getCorreo() + " · agenda por el paciente")))
            .andExpect(content().string(containsString("value=\"0\" selected=\"selected\"")));

        assertThat(agendarCon(p, familiar.getIdCuenta().toString(), dia.atTime(9, 0))).isEqualTo(familiar.getIdCuenta());
        assertThat(agendarCon(p, ajena.getIdCuenta().toString(), dia.atTime(10, 0))).isNull();
        assertThat(agendarCon(p, "0", dia.atTime(11, 0))).isNull();

        // Con cuenta propia, esa queda elegida por defecto.
        Cuenta conCuenta = datos.crearCuentaPaciente("propia" + System.nanoTime() + "@prueba.pe");
        mvc.perform(get("/recepcion/cita/nueva").with(user(recepcion))
                .param("idPaciente", conCuenta.getPacientePropio().getIdPaciente().toString()))
            .andExpect(content().string(containsString("value=\"" + conCuenta.getIdCuenta() + "\" selected=\"selected\"")))
            .andExpect(content().string(containsString("cuenta propia del paciente")));
    }

    /** PA-152 */
    @Test
    void laFichaYLaNuevaCitaOfrecenLaCitaIndicadaPendiente() throws Exception {
        Paciente p = datos.crearPaciente(doc());
        Cita origen = datos.crearCita(medico, p, LocalDateTime.now().minusDays(3).withHour(9).withMinute(0)
            .withSecond(0).withNano(0), 30, EstadoCita.REALIZADA);
        Long id = indicadas.indicar(origen.getIdCita(), new IndicacionComando(tipo.getIdTipoConsulta(), 30,
            LocalDate.now().plusDays(2), null, "Control de articulaciones"), null).getIdIndicacion();

        mvc.perform(get("/recepcion/pacientes/" + p.getIdPaciente()).with(user(recepcion)))
            .andExpect(content().string(containsString("Citas indicadas pendientes por agendar")))
            .andExpect(content().string(containsString("Control de articulaciones")))
            .andExpect(content().string(containsString("/recepcion/cita/nueva?indicacion=" + id)));
        mvc.perform(get("/recepcion/cita/nueva").with(user(recepcion)).param("idPaciente", p.getIdPaciente().toString()))
            .andExpect(content().string(containsString("Este paciente tiene una cita indicada pendiente")))
            .andExpect(content().string(containsString("indicacion=" + id)));
    }

    /** PA-154 */
    @Test
    void registrarAlPacienteDesdeLaNuevaCitaVuelveConElYaElegido() throws Exception {
        String documento = doc();
        mvc.perform(get("/recepcion/cita/nueva").with(user(recepcion)).param("q", documento))
            .andExpect(content().string(containsString("/recepcion/pacientes/nuevo?volver=cita&amp;q=" + documento)));
        mvc.perform(get("/recepcion/pacientes/nuevo").with(user(recepcion)).param("volver", "cita").param("q", documento))
            .andExpect(content().string(containsString("value=\"" + documento + "\"")))
            .andExpect(content().string(containsString("Guardar y volver a la cita")));
        mvc.perform(post("/recepcion/pacientes").with(user(recepcion)).with(csrf()).param("volver", "cita")
                .param("tipoDocumento", "DNI").param("nroDocumento", documento).param("nombres", "Rosa")
                .param("apellidos", "Huamán Lara").param("fechaNacimiento", "1975-06-20").param("telefono", "987654321"))
            .andExpect(redirectedUrlPattern("/recepcion/cita/nueva?idPaciente=*"));
        Long id = jdbc.queryForObject("select id_paciente from paciente where nro_documento = ?", Long.class, documento);
        mvc.perform(get("/recepcion/cita/nueva").with(user(recepcion)).param("idPaciente", id.toString()))
            .andExpect(content().string(containsString("Rosa Huamán Lara")))
            .andExpect(content().string(containsString("2 · Contacto, canal, tipo y médico")));
    }
}
