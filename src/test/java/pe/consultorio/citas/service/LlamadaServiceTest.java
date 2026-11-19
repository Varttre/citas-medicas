package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.FilaCola;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;

/** Cada llamada queda registrada y aplica su efecto; un resultado ajeno a la cola no deja nada. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class LlamadaServiceTest {

    @Autowired LlamadaService llamadaService;
    @Autowired ColaService colas;
    @Autowired PreferenciaService preferencias;
    @Autowired DatosPrueba datos;
    @Autowired CitaRepository citas;
    @Autowired CitaEventoRepository eventos;
    @Autowired OportunidadRepository oportunidades;
    @Autowired RegistroLlamadaRepository llamadas;

    Medico medico;
    Cuenta recepcion;

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Llamadas", datos.crearEspecialidad("Alergología"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(0, 0), LocalTime.of(23, 45));
        }
        recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION);
    }

    /** Una cita dentro de la cola de confirmaciones (empieza en unas horas). */
    private Cita enLaCola(int horas) {
        LocalDateTime t = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(horas);
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), t, 30,
            EstadoCita.PROGRAMADA);
    }

    private long llamadasDe(Cita c) {
        return llamadas.findAll().stream().filter(l -> l.getCita() != null
            && l.getCita().getIdCita().equals(c.getIdCita())).count();
    }

    @Test
    void confirmoDejaLaCitaConfirmadaConSuLlamada() {
        Cita c = enLaCola(20);
        assertThat(colas.confirmaciones(null, medico.getIdMedico(), null)).extracting(FilaCola::id)
            .contains(c.getIdCita());
        RegistroLlamada l = llamadaService.registrarConfirmacion(c.getIdCita(), ResultadoLlamada.CONFIRMO, null,
            recepcion.getIdCuenta());
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        assertThat(eventos.findAll()).anyMatch(e -> e.getLlamada() != null
            && e.getLlamada().getIdLlamada().equals(l.getIdLlamada()));
        assertThat(colas.confirmaciones(null, medico.getIdMedico(), null)).extracting(FilaCola::id)
            .doesNotContain(c.getIdCita());
    }

    @Test
    void noContestoSumaUnIntentoYCanceloLiberaElHorario() {
        Cita c = enLaCola(22);
        llamadaService.registrarConfirmacion(c.getIdCita(), ResultadoLlamada.NO_CONTESTO, null, recepcion.getIdCuenta());
        FilaCola fila = colas.confirmaciones(null, medico.getIdMedico(), null).stream()
            .filter(f -> f.id().equals(c.getIdCita())).findFirst().orElseThrow();
        assertThat(fila.intentos()).isEqualTo(1);
        assertThat(fila.estado()).isEqualTo("No contestó");

        assertThatThrownBy(() -> llamadaService.registrarConfirmacion(c.getIdCita(), ResultadoLlamada.CANCELO, " ",
            recepcion.getIdCuenta())).hasMessage("Indica el motivo de la cancelación.");
        llamadaService.registrarConfirmacion(c.getIdCita(), ResultadoLlamada.CANCELO, "Viaja por trabajo",
            recepcion.getIdCuenta());
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado())
            .isEqualTo(EstadoCita.CANCELADA_POR_PACIENTE);
        assertThat(oportunidades.findAll()).anyMatch(o -> o.getCitaOrigen().getIdCita().equals(c.getIdCita())
            && o.getEstado() == EstadoOportunidad.ABIERTA);
    }

    @Test
    void unResultadoDeOtraColaSeRechazaSinRegistrarNada() {
        Cita c = enLaCola(21);
        assertThatThrownBy(() -> llamadaService.registrarConfirmacion(c.getIdCita(), ResultadoLlamada.ACEPTO, null,
            recepcion.getIdCuenta())).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("confirmaciones");
        assertThat(llamadasDe(c)).isZero();
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void elCandidatoQueAceptaAdelantaSuCitaYElCupoQuedaCubierto() {
        LocalDate manana = LocalDate.now().plusDays(1);
        Cita cancelada = datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            manana.atTime(9, 0), 30, EstadoCita.CANCELADA_POR_PACIENTE);
        Oportunidad o = new Oportunidad();
        o.setCitaOrigen(cancelada);
        o.setMedico(medico);
        o.setInicio(manana.atTime(9, 0));
        o.setFin(manana.atTime(9, 30));
        o.setEstado(EstadoOportunidad.ABIERTA);
        o.setCreadaEn(LocalDateTime.now());
        o = oportunidades.save(o);
        Cita lejana = datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            manana.plusDays(3).atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        preferencias.guardar(lejana.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 5), null);

        List<FilaCola> cupos = colas.cupos();
        Long idOp = o.getIdOportunidad();
        assertThat(cupos).anyMatch(f -> f.id().equals(idOp) && f.detalle().contains("1 candidato"));

        llamadaService.registrarCupo(idOp, lejana.getIdCita(), 1, ResultadoLlamada.ACEPTO, null, manana.atTime(9, 0),
            recepcion.getIdCuenta());
        assertThat(citas.findById(lejana.getIdCita()).orElseThrow().getInicio()).isEqualTo(manana.atTime(9, 0));
        Oportunidad cubierta = oportunidades.findById(idOp).orElseThrow();
        assertThat(cubierta.getEstado()).isEqualTo(EstadoOportunidad.CUBIERTA);
        assertThat(cubierta.getCitaCobertura().getIdCita()).isEqualTo(lejana.getIdCita());
    }
}
