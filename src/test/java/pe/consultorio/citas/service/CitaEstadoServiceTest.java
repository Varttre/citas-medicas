package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.exception.PlazoVencidoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;

/** Transiciones de la cita con sus plazos y eventos. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class CitaEstadoServiceTest {

    @Autowired
    CitaEstadoService estados;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CitaRepository citas;

    @Autowired
    CitaEventoRepository eventos;

    Medico medico;
    static final AtomicInteger DOCUMENTO = new AtomicInteger(83000000);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Estados", datos.crearEspecialidad("Urología"));
    }

    /** Hora de la rejilla de 15 minutos desplazada desde ahora. */
    private static LocalDateTime enRejilla(int minutos) {
        LocalDateTime t = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            .plusMinutes((LocalDateTime.now().getMinute() / 15) * 15L);
        return t.plusMinutes(minutos);
    }

    private Cita cita(LocalDateTime inicio) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(DOCUMENTO.getAndIncrement())), inicio, 30,
            EstadoCita.PROGRAMADA);
    }

    private long eventosDe(Cita c) {
        return eventos.findAll().stream().filter(e -> e.getCita().getIdCita().equals(c.getIdCita())).count();
    }

    @Test
    void cancelarAntesDelInicioSiDespuesNo() {
        Cita futura = cita(enRejilla(24 * 60));
        estados.cancelarPorPaciente(futura.getIdCita(), null, null);
        assertThat(citas.findById(futura.getIdCita()).orElseThrow().getEstado())
            .isEqualTo(EstadoCita.CANCELADA_POR_PACIENTE);
        assertThat(eventosDe(futura)).isEqualTo(1);

        Cita empezada = cita(enRejilla(-15));
        assertThatThrownBy(() -> estados.cancelarPorPaciente(empezada.getIdCita(), null, null))
            .isInstanceOf(PlazoVencidoException.class);
    }

    @Test
    void noAsistioSoloDesdeCincoMinutosDespuesDelInicio() {
        Cita futura = cita(enRejilla(60));
        assertThatThrownBy(() -> estados.marcarNoAsistio(futura.getIdCita(), null))
            .isInstanceOf(PlazoVencidoException.class)
            .hasMessageContaining("se habilita");
        Cita pasada = cita(enRejilla(-15));
        estados.marcarNoAsistio(pasada.getIdCita(), null);
        assertThat(citas.findById(pasada.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.NO_ASISTIO);
    }

    @Test
    void llegadaAtencionYFin() {
        Cita c = cita(enRejilla(15));
        assertThatThrownBy(() -> estados.finalizarAtencion(c.getIdCita(), null))
            .isInstanceOf(ReglaNegocioException.class);
        estados.marcarLlegada(c.getIdCita(), null);
        estados.iniciarAtencion(c.getIdCita(), null);
        estados.finalizarAtencion(c.getIdCita(), null);
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.REALIZADA);
    }

    @Test
    void elRetiroExigeMotivo() {
        Cita c = cita(enRejilla(15));
        estados.marcarLlegada(c.getIdCita(), null);
        assertThatThrownBy(() -> estados.marcarRetiro(c.getIdCita(), null, " "))
            .hasMessage("Indica el motivo del retiro.");
        estados.marcarRetiro(c.getIdCita(), null, "Se fue por una emergencia");
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.NO_ASISTIO);
    }
}
