package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;

/** Reprogramación: misma cita, mismo id, vuelve a PROGRAMADA; los rechazos no tocan la cita. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class ReprogramacionServiceTest {

    @Autowired ReprogramacionService reprogramacion;
    @Autowired PreferenciaService preferencias;
    @Autowired DatosPrueba datos;
    @Autowired CitaRepository citas;
    @Autowired CitaEventoRepository eventos;
    @Autowired OportunidadRepository oportunidades;
    @Autowired PreferenciaDisponibilidadRepository preferenciasRepo;

    Medico medico;
    LocalDate manana = LocalDate.now().plusDays(1);
    static final AtomicInteger DOCUMENTO = new AtomicInteger(85000000);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Reprograma", datos.crearEspecialidad("Endocrinología"));
        for (int dia = 1; dia <= 7; dia++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), dia, LocalTime.of(8, 0), LocalTime.of(20, 0));
        }
    }

    private Cita cita(LocalDateTime inicio, EstadoCita estado) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(DOCUMENTO.getAndIncrement())), inicio, 30,
            estado);
    }

    private Cita releer(Cita c) {
        return citas.findById(c.getIdCita()).orElseThrow();
    }

    private List<CitaEvento> eventosDe(Cita c) {
        return eventos.findAll().stream().filter(e -> e.getCita().getIdCita().equals(c.getIdCita())).toList();
    }

    @Test
    void unaCitaConfirmadaReprogramadaConservaSuIdYVuelveAProgramada() {
        Cita c = cita(manana.atTime(10, 0), EstadoCita.CONFIRMADA);
        reprogramacion.reprogramar(c.getIdCita(), manana.atTime(15, 0), null);

        Cita r = releer(c);
        assertThat(r.getInicio()).isEqualTo(manana.atTime(15, 0));
        assertThat(r.getFin()).isEqualTo(manana.atTime(15, 30));
        assertThat(r.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        CitaEvento e = eventosDe(c).get(0);
        assertThat(e.getTipoEvento()).isEqualTo(TipoEvento.REPROGRAMACION);
        assertThat(e.getEstadoAnterior()).isEqualTo(EstadoCita.CONFIRMADA);
        assertThat(e.getInicioAnterior()).isEqualTo(manana.atTime(10, 0));
        assertThat(e.getInicioNuevo()).isEqualTo(manana.atTime(15, 0));
    }

    @Test
    void puedeCorrerseSobreSuPropioHorario() {
        Cita c = cita(manana.atTime(11, 0), EstadoCita.PROGRAMADA);
        reprogramacion.reprogramar(c.getIdCita(), manana.atTime(11, 15), null);
        assertThat(releer(c).getInicio()).isEqualTo(manana.atTime(11, 15));
    }

    @Test
    void unHorarioOcupadoOFueraDelHorarioSeRechazaYLaCitaQuedaIgual() {
        Cita c = cita(manana.atTime(9, 0), EstadoCita.PROGRAMADA);
        cita(manana.atTime(16, 0), EstadoCita.PROGRAMADA);

        assertThatThrownBy(() -> reprogramacion.reprogramar(c.getIdCita(), manana.atTime(16, 0), null))
            .isInstanceOf(HorarioOcupadoException.class);
        assertThatThrownBy(() -> reprogramacion.reprogramar(c.getIdCita(), manana.atTime(21, 0), null))
            .isInstanceOf(HorarioOcupadoException.class);
        assertThatThrownBy(() -> reprogramacion.reprogramar(c.getIdCita(), manana.atTime(12, 10), null))
            .isInstanceOf(ReglaNegocioException.class);
        assertThat(releer(c).getInicio()).isEqualTo(manana.atTime(9, 0));
        assertThat(eventosDe(c)).isEmpty();
    }

    @Test
    void unaCitaQueYaEmpezoONoVigenteNoSeReprograma() {
        LocalDateTime hace15 = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            .plusMinutes((LocalDateTime.now().getMinute() / 15) * 15L).minusMinutes(15);
        Cita empezada = cita(hace15, EstadoCita.PROGRAMADA);
        assertThatThrownBy(() -> reprogramacion.reprogramar(empezada.getIdCita(), manana.atTime(13, 0), null))
            .hasMessage("Esta cita ya no se puede reprogramar.");
        Cita cancelada = cita(manana.atTime(13, 0), EstadoCita.CANCELADA_POR_PACIENTE);
        assertThatThrownBy(() -> reprogramacion.reprogramar(cancelada.getIdCita(), manana.atTime(14, 0), null))
            .hasMessage("Esta cita ya no se puede reprogramar.");
    }

    @Test
    void unMedicoNoReprogramaCitasDeOtro() {
        Cita c = cita(manana.atTime(17, 0), EstadoCita.PROGRAMADA);
        Long otro = datos.crearMedico("Ajeno", datos.crearEspecialidad("Endocrinología")).getCuenta().getIdCuenta();
        assertThatThrownBy(() -> reprogramacion.reprogramar(c.getIdCita(), manana.atTime(18, 0), otro))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void adelantarPorCupoMueveLaCitaYConsumeLaPreferencia() {
        Cita origen = cita(manana.atTime(8, 0), EstadoCita.CANCELADA_POR_PACIENTE);
        Oportunidad o = new Oportunidad();
        o.setCitaOrigen(origen);
        o.setMedico(medico);
        o.setInicio(manana.atTime(8, 0));
        o.setFin(manana.atTime(8, 30));
        o.setEstado(EstadoOportunidad.ABIERTA);
        o.setCreadaEn(LocalDateTime.now());
        o = oportunidades.save(o);

        Cita lejana = cita(manana.plusDays(3).atTime(10, 0), EstadoCita.PROGRAMADA);
        preferencias.guardar(lejana.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 30, 0), null);

        Long idOportunidad = o.getIdOportunidad();
        assertThatThrownBy(() -> reprogramacion.adelantarPorCupo(lejana.getIdCita(), idOportunidad,
            manana.atTime(8, 15), null)).hasMessageContaining("no cabe");

        reprogramacion.adelantarPorCupo(lejana.getIdCita(), idOportunidad, manana.atTime(8, 0), null);
        assertThat(releer(lejana).getInicio()).isEqualTo(manana.atTime(8, 0));
        assertThat(preferenciasRepo.findByCitaIdCita(lejana.getIdCita()).orElseThrow().getRevocadoEn()).isNotNull();
    }
}
