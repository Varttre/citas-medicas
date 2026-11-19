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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** Nueva cita para otra persona y con disponibilidad inmediata; un horario ocupado no hace perder lo ingresado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class NuevaCitaTerceroTest {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;
    @Autowired PacienteRepository pacientes;
    @Autowired CuentaPacienteRepository vinculos;
    @Autowired PreferenciaDisponibilidadRepository preferencias;

    Especialidad especialidad;
    Medico medico;
    Cuenta cuenta;
    UsuarioSesion paciente;
    LocalDate dia = LocalDate.now().plusDays(2);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Neurología");
        medico = datos.crearMedico("Tercero", especialidad);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(12, 0));
        }
        cuenta = datos.crearCuentaPaciente("tercero" + System.nanoTime() + "@prueba.pe");
        paciente = detalles.loadUserByUsername(cuenta.getCorreo());
    }

    /** Paso 1 para otra persona → paso 4 con el médico → elige la hora. Devuelve la cookie de sesión. */
    private Cookie hastaElPaso5(String documento, LocalDate nacimiento, LocalDateTime hora) throws Exception {
        Cookie sesion = mvc.perform(get("/paciente/cita/nueva").with(user(paciente)))
            .andReturn().getResponse().getCookie("SESSION");
        mvc.perform(post("/paciente/cita/nueva").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("paraQuien", "OTRA").param("tipoDocumento", "DNI").param("nroDocumento", documento)
                .param("nombres", "Mateo").param("apellidos", "Vega Ríos")
                .param("fechaNacimiento", nacimiento.toString()).param("consentimiento", "true"))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2"));
        mvc.perform(get("/paciente/cita/nueva/4").with(user(paciente)).cookie(sesion)
            .param("medico", medico.getIdMedico().toString())
            .param("especialidad", especialidad.getIdEspecialidad().toString()));
        mvc.perform(post("/paciente/cita/nueva/4").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("inicio", hora.format(T)))
            .andExpect(redirectedUrl("/paciente/cita/nueva/5"));
        return sesion;
    }

    @Test
    void agendaParaUnTerceroNuevoConDisponibilidadInmediata() throws Exception {
        String documento = String.valueOf(70000000 + System.nanoTime() % 9999999);
        Cookie sesion = hastaElPaso5(documento, LocalDate.of(2019, 4, 8), dia.atTime(9, 0));
        mvc.perform(get("/paciente/cita/nueva/5").with(user(paciente)).cookie(sesion))
            .andExpect(content().string(containsString("Mateo Vega Ríos")))
            .andExpect(content().string(containsString("Acepto ser llamado si se libera un cupo antes")));
        mvc.perform(post("/paciente/cita/nueva/5").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("aceptaTerminos", "true").param("aceptaLlamada", "true").param("alcance", "MEDICO")
                .param("llegada", "1").param("unidadLlegada", "HORAS").param("diasPrevios", "2"))
            .andExpect(redirectedUrl("/paciente/citas"));

        Paciente tercero = pacientes.findAll().stream().filter(p -> p.getNroDocumento().equals(documento))
            .findFirst().orElseThrow();
        assertThat(tercero.getTelefono()).isNull();
        Cita c = citas.delPaciente(tercero.getIdPaciente()).get(0);
        assertThat(c.getCuentaTitular().getIdCuenta()).isEqualTo(cuenta.getIdCuenta());
        assertThat(vinculos.delPaciente(tercero.getIdPaciente()))
            .anyMatch(v -> v.getCuenta().getIdCuenta().equals(cuenta.getIdCuenta()));
        assertThat(preferencias.findByCitaIdCita(c.getIdCita()).orElseThrow().getMinLlegadaMin())
            .isEqualTo((short) 60);
    }

    @Test
    void unTerceroExistenteConOtraFechaOSinConsentimientoSeRechaza() throws Exception {
        Paciente existente = datos.crearPaciente(String.valueOf(71000000 + System.nanoTime() % 999999));
        mvc.perform(post("/paciente/cita/nueva").with(user(paciente)).with(csrf())
                .param("paraQuien", "OTRA").param("tipoDocumento", "DNI")
                .param("nroDocumento", existente.getNroDocumento()).param("nombres", "Otro").param("apellidos", "Nombre")
                .param("fechaNacimiento", "2001-01-01").param("consentimiento", "true"))
            .andExpect(redirectedUrl("/paciente/cita/nueva"))
            .andExpect(flash().attribute("error", "Revisa los datos o comunícate con el consultorio."));
        mvc.perform(post("/paciente/cita/nueva").with(user(paciente)).with(csrf())
                .param("paraQuien", "OTRA").param("tipoDocumento", "DNI").param("nroDocumento", "72000001")
                .param("nombres", "Ana").param("apellidos", "Ruiz").param("fechaNacimiento", "2015-05-05"))
            .andExpect(flash().attribute("error", containsString("consentimiento")));
        assertThat(vinculos.delPaciente(existente.getIdPaciente())).isEmpty();
        assertThat(pacientes.findById(existente.getIdPaciente()).orElseThrow().getNombres()).isEqualTo("Paciente");
    }

    @Test
    void siElHorarioSeOcupaVuelveAHorariosConservandoLaDisponibilidadInmediata() throws Exception {
        String documento = String.valueOf(73000000 + System.nanoTime() % 999999);
        Cookie sesion = hastaElPaso5(documento, LocalDate.of(2018, 2, 2), dia.atTime(10, 0));
        datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), dia.atTime(10, 0),
            30, EstadoCita.PROGRAMADA);
        mvc.perform(post("/paciente/cita/nueva/5").with(user(paciente)).with(csrf()).cookie(sesion)
                .param("aceptaTerminos", "true").param("aceptaLlamada", "true").param("alcance", "ESPECIALIDAD")
                .param("llegada", "30").param("diasPrevios", "1"))
            .andExpect(redirectedUrl("/paciente/cita/nueva/4"))
            .andExpect(flash().attribute("error", containsString("acaba de ser ocupado")));

        mvc.perform(post("/paciente/cita/nueva/4").with(user(paciente)).with(csrf()).cookie(sesion)
            .param("inicio", dia.atTime(11, 0).format(T)));
        mvc.perform(get("/paciente/cita/nueva/5").with(user(paciente)).cookie(sesion))
            .andExpect(content().string(containsString("value=\"ESPECIALIDAD\" checked=\"checked\"")))
            .andExpect(content().string(containsString("Mateo Vega Ríos")));
    }
}
