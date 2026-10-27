package pe.consultorio.citas.service.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.service.HorarioService;

/** Disponibilidad real de un médico: franjas menos citas, retenciones de otras cuentas y excepciones. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class DisponibilidadServiceTest {

    @Autowired
    DisponibilidadService disponibilidad;

    @Autowired
    BloqueoTemporalService bloqueos;

    @Autowired
    HorarioService horarioService;

    @Autowired
    DatosPrueba datos;

    Medico medico;
    LocalDate lunes;

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Lunes", datos.crearEspecialidad("Dermatología"));
        lunes = LocalDate.now().plusDays(14).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        datos.crearHorario(medico, LocalDate.now().minusDays(1), 1, LocalTime.of(8, 0), LocalTime.of(12, 0));
    }

    private List<LocalDateTime> inicios(Long idCuenta) {
        return disponibilidad.ofertasDelDia(medico.getIdMedico(), 30, lunes, idCuenta).stream()
            .map(Oferta::inicio).toList();
    }

    @Test
    void unaCitaYUnaRetencionDeOtraCuentaQuitanSusHorarios() {
        datos.crearCita(medico, datos.crearPaciente("80000001"), lunes.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        Cuenta otra = datos.crearCuentaPaciente("retiene" + System.nanoTime() + "@prueba.pe");
        Cuenta yo = datos.crearCuentaPaciente("pide" + System.nanoTime() + "@prueba.pe");
        bloqueos.tomar(otra.getIdCuenta(), medico.getIdMedico(), lunes.atTime(8, 45), lunes.atTime(9, 0));

        List<LocalDateTime> paraMi = inicios(yo.getIdCuenta());
        assertThat(paraMi).doesNotContain(lunes.atTime(9, 0), lunes.atTime(8, 45))
            .contains(lunes.atTime(8, 0), lunes.atTime(9, 30));
        // quien retiene sí ve su propio horario
        assertThat(inicios(otra.getIdCuenta())).contains(lunes.atTime(8, 30));

        assertThatThrownBy(() -> bloqueos.tomar(yo.getIdCuenta(), medico.getIdMedico(), lunes.atTime(8, 30),
            lunes.atTime(9, 0))).isInstanceOf(HorarioOcupadoException.class);
    }

    @Test
    void unaExcepcionDeDiaCompletoDejaElDiaSinOfertas() {
        horarioService.crearExcepcion(medico.getIdMedico(), lunes, lunes, null, null, "Feriado", null);
        assertThat(inicios(null)).isEmpty();
    }

    @Test
    void lasProximasOfertasSaltanLosDiasSinHorario() {
        List<OfertaDia> dias = disponibilidad.ofertasProximas(medico.getIdMedico(), 30, lunes.minusDays(3), null);
        assertThat(dias).hasSize(3);
        assertThat(dias).allMatch(d -> d.fecha().getDayOfWeek() == DayOfWeek.MONDAY);
        assertThat(dias.get(0).fecha()).isEqualTo(lunes);
    }
}
