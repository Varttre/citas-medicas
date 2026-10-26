package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.dto.HorarioMedicoDatos;
import pe.consultorio.citas.dto.VersionHorarioDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.exception.HorarioConCitasException;
import pe.consultorio.citas.exception.ReglaNegocioException;

/**
Reglas de «Mi horario» contra PostgreSQL real. Las fechas están en 2031 para que siempre sean futuras, sin depender
del día en que se corra la prueba.
*/
@SpringBootTest
@Import(PostgresTestConfig.class)
class HorarioServiceTest {

    private static final int MARTES = 2;
    private static final LocalDate VERSION_INICIAL = LocalDate.of(2031, 1, 1);
    private static final LocalDate NUEVA_VERSION = LocalDate.of(2031, 2, 3);

    @Autowired
    HorarioService horarios;

    @Autowired
    DatosPrueba datos;

    @Autowired
    JdbcTemplate jdbc;

    private Medico medicoConMartesDeOchoASeis() {
        Medico m = datos.crearMedico("Carla", datos.crearEspecialidad("Medicina general"));
        datos.crearHorario(m, VERSION_INICIAL, MARTES, LocalTime.of(8, 0), LocalTime.of(18, 0));
        return m;
    }

    private static LocalDate primerMartesDesde(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    private Long cuentaDe(Medico m) {
        return m.getCuenta().getIdCuenta();
    }

    private int rechazosAuditados(Medico m) {
        return jdbc.queryForObject("select count(*) from auditoria where accion = 'CAMBIO_HORARIO_RECHAZADO' "
            + "and id_registro = ?", Integer.class, String.valueOf(m.getIdMedico()));
    }

    @Test
    void unaVersionQueDejaFueraUnaCitaSeRechazaListandolaYQuedaAuditada() {
        Medico m = medicoConMartesDeOchoASeis();
        LocalDateTime alasCinco = primerMartesDesde(LocalDate.of(2031, 3, 1)).atTime(17, 0);
        Cita cita = datos.crearCita(m, datos.crearPaciente("51000001"), alasCinco, 30, EstadoCita.PROGRAMADA);

        List<FranjaDatos> martesHastaLasCuatro = List.of(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        assertThatThrownBy(() -> horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION, martesHastaLasCuatro, cuentaDe(m)))
            .isInstanceOfSatisfying(HorarioConCitasException.class, e -> {
                assertThat(e.getMessage()).isEqualTo("Este cambio deja fuera 1 cita agendada. Reprográmalas primero.");
                assertThat(e.getCitas()).singleElement()
                    .satisfies(a -> assertThat(a.idCita()).isEqualTo(cita.getIdCita()));
            });
        assertThat(rechazosAuditados(m)).isEqualTo(1);
        // La operación se deshizo: sigue habiendo una sola versión, sin cerrar
        assertThat(horarios.verHorario(m.getIdMedico()).versiones()).singleElement()
            .satisfies(v -> assertThat(v.vigenteHasta()).isNull());
    }

    @Test
    void sinCitasAfectadasLaVersionSeAceptaYLaAnteriorSeCierraElDiaPrevio() {
        Medico m = medicoConMartesDeOchoASeis();

        horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION,
            List.of(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(16, 0))), cuentaDe(m));

        List<VersionHorarioDatos> versiones = horarios.verHorario(m.getIdMedico()).versiones();
        assertThat(versiones).hasSize(2);
        VersionHorarioDatos nueva = versiones.get(0);
        VersionHorarioDatos anterior = versiones.get(1);
        assertThat(nueva.vigenteDesde()).isEqualTo(NUEVA_VERSION);
        assertThat(nueva.vigenteHasta()).isNull();
        assertThat(nueva.franjas()).containsExactly(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(16, 0)));
        assertThat(anterior.vigenteHasta()).isEqualTo(NUEVA_VERSION.minusDays(1));
        assertThat(rechazosAuditados(m)).isZero();
    }

    @Test
    void unaCitaQueSigueCabiendoNoImpideElCambio() {
        Medico m = medicoConMartesDeOchoASeis();
        datos.crearCita(m, datos.crearPaciente("51000002"),
            primerMartesDesde(LocalDate.of(2031, 3, 1)).atTime(9, 0), 30, EstadoCita.PROGRAMADA);

        horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION,
            List.of(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(16, 0))), cuentaDe(m));

        assertThat(horarios.verHorario(m.getIdMedico()).versiones()).hasSize(2);
    }

    @Test
    void lasCitasCanceladasNoImpidenElCambio() {
        Medico m = medicoConMartesDeOchoASeis();
        datos.crearCita(m, datos.crearPaciente("51000003"),
            primerMartesDesde(LocalDate.of(2031, 3, 1)).atTime(17, 0), 30, EstadoCita.CANCELADA_POR_PACIENTE);

        horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION,
            List.of(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(16, 0))), cuentaDe(m));

        assertThat(horarios.verHorario(m.getIdMedico()).versiones()).hasSize(2);
    }

    @Test
    void noSePuedeProgramarAntesDeUnaVersionYaProgramada() {
        Medico m = medicoConMartesDeOchoASeis();
        List<FranjaDatos> franjas = List.of(new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        horarios.crearVersion(m.getIdMedico(), LocalDate.of(2031, 6, 1), franjas, cuentaDe(m));

        assertThatThrownBy(() -> horarios.crearVersion(m.getIdMedico(), LocalDate.of(2031, 5, 1), franjas, cuentaDe(m)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Ya hay una versión programada desde el 01/06. Elimínala o cámbiala.");
    }

    @Test
    void franjasQueSeCruzanOFueraDeLaRejillaSeRechazan() {
        Medico m = medicoConMartesDeOchoASeis();

        assertThatThrownBy(() -> horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION, List.of(
                new FranjaDatos(MARTES, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new FranjaDatos(MARTES, LocalTime.of(11, 0), LocalTime.of(14, 0))), cuentaDe(m)))
            .hasMessage("Las franjas del martes se cruzan.");
        assertThatThrownBy(() -> horarios.crearVersion(m.getIdMedico(), NUEVA_VERSION, List.of(
                new FranjaDatos(MARTES, LocalTime.of(8, 10), LocalTime.of(12, 0))), cuentaDe(m)))
            .hasMessageContaining("múltiplos de 15 minutos");
    }

    @Test
    void unaExcepcionQueCubreUnaCitaSeRechazaYEnOtroHorarioSeAcepta() {
        Medico m = medicoConMartesDeOchoASeis();
        LocalDate dia = primerMartesDesde(LocalDate.of(2031, 3, 1));
        datos.crearCita(m, datos.crearPaciente("51000004"), dia.atTime(10, 0), 30, EstadoCita.CONFIRMADA);

        assertThatThrownBy(() -> horarios.crearExcepcion(m.getIdMedico(), dia, dia,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "Congreso", cuentaDe(m)))
            .isInstanceOf(HorarioConCitasException.class)
            .hasMessage("Esta excepción cubre 1 cita agendada. Reprográmalas primero.");

        horarios.crearExcepcion(m.getIdMedico(), dia, dia, LocalTime.of(14, 0), LocalTime.of(16, 0), "Trámite",
            cuentaDe(m));
        assertThat(horarios.verHorario(m.getIdMedico()).excepciones()).singleElement()
            .satisfies(e -> assertThat(e.motivo()).isEqualTo("Trámite"));
    }

    @Test
    void laDuracionDebeSerMultiploDeQuinceYSuCambioQuedaAuditado() {
        Medico m = medicoConMartesDeOchoASeis();
        HorarioMedicoDatos antes = horarios.verHorario(m.getIdMedico());
        Short idTipo = antes.duraciones().get(0).idTipoConsulta();

        assertThatThrownBy(() -> horarios.cambiarDuracion(m.getIdMedico(), idTipo, 40, cuentaDe(m)))
            .hasMessage("La duración debe ser un múltiplo de 15 minutos.");

        horarios.cambiarDuracion(m.getIdMedico(), idTipo, 45, cuentaDe(m));

        assertThat(horarios.verHorario(m.getIdMedico()).duraciones().get(0).duracionMin()).isEqualTo(45);
        Integer auditados = jdbc.queryForObject("select count(*) from auditoria where accion = 'CONFIG_CAMBIO' "
            + "and id_registro = ?", Integer.class, m.getIdMedico() + ":" + idTipo);
        assertThat(auditados).isEqualTo(1);
    }
}
