package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.Parentesco;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Bloque 5 (RC-046): la elegibilidad de «Acepto ser llamado» se fija al sacar la cita y se recalcula al reprogramar,
los días van de 1 a los que faltan, y el tercero guarda su parentesco.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class Bloque5ModeloTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired ReservaService reserva;
    @Autowired ReprogramacionService reprogramacion;
    @Autowired PreferenciaService preferencias;
    @Autowired PreferenciaDisponibilidadRepository preferenciasRepo;
    @Autowired RegistroService registro;
    @Autowired CuentaPacienteRepository vinculos;
    @Autowired ConfiguracionService configuracion;
    @Autowired CitaRepository citas;
    @Autowired UsuarioDetallesService detalles;
    @Autowired JdbcTemplate jdbc;

    Especialidad especialidad;
    Medico medico;
    Short idTipo;

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Reumatología");
        medico = datos.crearMedico("Bloque5", especialidad);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(0, 0), LocalTime.of(23, 45));
        }
        idTipo = configuracion.tiposWeb().get(0).getIdTipoConsulta();
    }

    private Cita agendar(Paciente p, LocalDateTime inicio) {
        return reserva.agendar(new NuevaCitaComando(p.getIdPaciente(), null, medico.getIdMedico(),
            especialidad.getIdEspecialidad(), idTipo, inicio, 30, Modalidad.PRESENCIAL, CanalOrigen.PRESENCIAL, null,
            null, null, null, false));
    }

    private Paciente paciente() {
        return datos.crearPaciente(String.valueOf(10000000 + System.nanoTime() % 89999999));
    }

    /** Un horario de la rejilla a pocas horas (menos de 24), sin rozar el fin del día. */
    private static LocalDateTime enPocasHoras() {
        LocalDateTime base = Rejilla.redondearArriba(LocalDateTime.now().plusHours(2), 15);
        return base.getHour() >= 21 ? base.toLocalDate().plusDays(1).atTime(1, 0) : base;
    }

    private Cita releer(Cita c) {
        return citas.findById(c.getIdCita()).orElseThrow();
    }

    /** PA-171 */
    @Test
    void laElegibilidadSeFijaAlSacarLaCitaYSeRecalculaAlReprogramar() {
        LocalDateTime pronto = enPocasHoras();
        Cita cerca = agendar(paciente(), pronto);
        Cita lejos = agendar(paciente(), LocalDate.now().plusDays(3).atTime(9, 0));
        assertThat(releer(cerca).isElegibleAdelanto()).isFalse();
        assertThat(releer(lejos).isElegibleAdelanto()).isTrue();
        assertThatThrownBy(() -> preferencias.guardar(cerca.getIdCita(),
            new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 1), null)).hasMessage(PreferenciaService.NO_ELEGIBLE);

        Short antes = configuracion.parametros().getMinHorasAdelanto();
        try {
            jdbc.update("update parametro_consultorio set min_horas_adelanto = 200 where id_parametro = 1");
            assertThat(releer(lejos).isElegibleAdelanto()).as("cambiar el parámetro no toca las citas ya sacadas")
                .isTrue();
        } finally {
            jdbc.update("update parametro_consultorio set min_horas_adelanto = ? where id_parametro = 1", antes);
        }

        preferencias.guardar(lejos.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 2), null);
        reprogramacion.reprogramar(lejos.getIdCita(), pronto.plusMinutes(60), null);
        assertThat(releer(lejos).isElegibleAdelanto()).isFalse();
        assertThat(preferenciasRepo.findByCitaIdCita(lejos.getIdCita()).orElseThrow().getRevocadoEn())
            .as("al quedar muy pronto, la preferencia se retira").isNotNull();
        reprogramacion.reprogramar(lejos.getIdCita(), LocalDate.now().plusDays(4).atTime(9, 0), null);
        assertThat(releer(lejos).isElegibleAdelanto()).isTrue();
    }

    /** PA-172 */
    @Test
    void losDiasAntesVanDeUnoALosQueFaltanParaLaCita() {
        Cita c = datos.crearCita(medico, paciente(), LocalDate.now().plusDays(3).atTime(10, 0), 30,
            EstadoCita.PROGRAMADA);
        assertThatThrownBy(() -> preferencias.guardar(c.getIdCita(),
            new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 0), null)).hasMessageContaining("al menos 1 día");
        preferencias.guardar(c.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 10), null);
        assertThat(preferenciasRepo.findByCitaIdCita(c.getIdCita()).orElseThrow().getDiasPrevios())
            .as("más de los que faltan = lo más pronto posible").isEqualTo((short) 3);

        Cita hoy = datos.crearCita(medico, paciente(), LocalDate.now().atTime(23, 0), 30, EstadoCita.PROGRAMADA);
        assertThatThrownBy(() -> preferencias.guardar(hoy.getIdCita(),
            new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 1), null)).hasMessageContaining("La cita es hoy");
        assertThatThrownBy(() -> jdbc.update("update preferencia_disponibilidad set dias_previos = 0 where id_cita = ?",
            c.getIdCita())).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** PA-173 */
    @Test
    void elTerceroGuardaSuParentescoYSeVeEnMisTerceros() throws Exception {
        Cuenta cuenta = datos.crearCuentaPaciente("parentesco" + System.nanoTime() + "@prueba.pe");
        UsuarioSesion u = detalles.loadUserByUsername(cuenta.getCorreo());
        String docHija = String.valueOf(71000000 + System.nanoTime() % 999999);
        Long idHija = registro.vincularTercero(cuenta.getIdCuenta(), new TerceroForm(TipoDocumento.DNI, docHija,
            "Hija", "Prueba", LocalDate.of(2016, 6, 6), null, true, Parentesco.HIJO)).idPaciente();
        assertThat(registro.terceros(cuenta.getIdCuenta())).singleElement()
            .satisfies(t -> assertThat(t.parentesco()).isEqualTo("Hijo/a"));

        String docMadre = String.valueOf(72000000 + System.nanoTime() % 999999);
        mvc.perform(post("/paciente/terceros").with(user(u)).with(csrf()).param("tipoDocumento", "DNI")
            .param("nroDocumento", docMadre).param("nombres", "Madre").param("apellidos", "Prueba")
            .param("fechaNacimiento", "1960-01-01").param("consentimiento", "true").param("parentesco", "MADRE"));
        mvc.perform(get("/paciente/terceros").with(user(u)))
            .andExpect(content().string(containsString("Hijo/a · DNI " + docHija)))
            .andExpect(content().string(containsString("Madre · DNI " + docMadre)));

        assertThatThrownBy(() -> jdbc.update("update cuenta_paciente set parentesco = 'TIO' where id_paciente = ?",
            idHija)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(vinculos.delPaciente(idHija)).singleElement()
            .satisfies(v -> assertThat(v.getParentesco()).isEqualTo("HIJO"));
    }
}
