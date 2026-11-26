package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.IndicacionComando;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoIndicacion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/** Citas indicadas por el médico: se indican desde una consulta atendida, recepción las llama y se agendan. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class CitaIndicadaServiceTest {

    @Autowired CitaIndicadaService indicadas;
    @Autowired CitaIndicadaRepository repo;
    @Autowired ReservaService reserva;
    @Autowired TipoConsultaRepository tipos;
    @Autowired DatosPrueba datos;

    private String doc() {
        return String.valueOf(System.nanoTime() % 100000000);
    }

    private Short tipo() {
        return tipos.findAll().stream().filter(t -> t.isActivo()).findFirst().orElseThrow().getIdTipoConsulta();
    }

    private Cita atendida(Medico m, Paciente p) {
        return datos.crearCita(m, p, LocalDateTime.now().minusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0),
            30, EstadoCita.REALIZADA);
    }

    @Test
    void indicarDesdeUnaConsultaAtendidaQuedaPendienteYLaVeRecepcion() {
        Medico m = datos.crearMedico("Indica", datos.crearEspecialidad("Neumología"));
        Paciente p = datos.crearPaciente(doc());
        Cita origen = atendida(m, p);

        CitaIndicada i = indicadas.indicar(origen.getIdCita(),
            new IndicacionComando(tipo(), 30, LocalDate.now().plusDays(10), null, "Traer placas"), null);

        assertThat(i.getEstado()).isEqualTo(EstadoIndicacion.PENDIENTE);
        assertThat(i.getVigenteHasta()).isAfter(i.getFechaRecomendada());
        assertThat(indicadas.pendientesVista()).anySatisfy(v -> {
            assertThat(v.idIndicacion()).isEqualTo(i.getIdIndicacion());
            assertThat(v.paciente()).isEqualTo(p.getNombres() + " " + p.getApellidos());
        });
        assertThat(indicadas.pendientesDePaciente(p.getIdPaciente())).hasSize(1);
    }

    @Test
    void noSeIndicaDesdeUnaCitaQueNoSeAtendio() {
        Medico m = datos.crearMedico("NoIndica", datos.crearEspecialidad("Reumatología"));
        Cita programada = datos.crearCita(m, datos.crearPaciente(doc()),
            LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0), 30, EstadoCita.PROGRAMADA);

        assertThatThrownBy(() -> indicadas.indicar(programada.getIdCita(),
            new IndicacionComando(tipo(), 30, LocalDate.now().plusDays(10), null, null), null))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void siNoLeInteresaSeDescartaConMotivoYSinMotivoNoSeRegistra() {
        Medico m = datos.crearMedico("Descarta", datos.crearEspecialidad("Nefrología"));
        Cita origen = atendida(m, datos.crearPaciente(doc()));
        CitaIndicada i = indicadas.indicar(origen.getIdCita(),
            new IndicacionComando(tipo(), 30, LocalDate.now().plusDays(5), null, null), null);
        Long recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION).getIdCuenta();

        assertThatThrownBy(() -> indicadas.registrarLlamada(i.getIdIndicacion(), ResultadoLlamada.NO_LE_INTERESA, " ",
            recepcion)).isInstanceOf(ReglaNegocioException.class);
        indicadas.registrarLlamada(i.getIdIndicacion(), ResultadoLlamada.NO_LE_INTERESA, "Se atiende en otra clínica",
            recepcion);

        CitaIndicada despues = repo.findById(i.getIdIndicacion()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoIndicacion.DESCARTADA);
        assertThat(despues.getMotivoDescarte()).isEqualTo("Se atiende en otra clínica");
    }

    @Test
    void agendarConLaIndicacionLaMarcaAgendadaYSoloParaSuPaciente() {
        Especialidad e = datos.crearEspecialidad("Geriatría");
        Medico m = datos.crearMedico("Agenda", e);
        Paciente p = datos.crearPaciente(doc());
        CitaIndicada i = indicadas.indicar(atendida(m, p).getIdCita(),
            new IndicacionComando(tipo(), 30, LocalDate.now().plusDays(5), null, null), null);
        LocalDateTime inicio = LocalDate.now().plusDays(6).atTime(11, 0);

        assertThatThrownBy(() -> reserva.agendar(new NuevaCitaComando(datos.crearPaciente(doc()).getIdPaciente(), null,
            m.getIdMedico(), e.getIdEspecialidad(), tipo(), inicio, 30, Modalidad.PRESENCIAL, CanalOrigen.TELEFONO, null,
            i.getIdIndicacion(), null, null, false))).isInstanceOf(ReglaNegocioException.class);

        Cita generada = reserva.agendar(new NuevaCitaComando(p.getIdPaciente(), null, m.getIdMedico(),
            e.getIdEspecialidad(), tipo(), inicio, 30, Modalidad.PRESENCIAL, CanalOrigen.TELEFONO, null,
            i.getIdIndicacion(), null, null, false));

        CitaIndicada despues = repo.findById(i.getIdIndicacion()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoIndicacion.AGENDADA);
        assertThat(despues.getCitaGenerada().getIdCita()).isEqualTo(generada.getIdCita());
    }

    @Test
    void laTareaProgramadaVenceLasIndicacionesFueraDeVigencia() {
        Medico m = datos.crearMedico("Vence", datos.crearEspecialidad("Hematología"));
        CitaIndicada i = indicadas.indicar(atendida(m, datos.crearPaciente(doc())).getIdCita(),
            new IndicacionComando(tipo(), 30, LocalDate.now(), LocalDate.now(), null), null);
        i.setFechaRecomendada(LocalDate.now().minusDays(10));
        i.setVigenteHasta(LocalDate.now().minusDays(1));
        repo.save(i);

        assertThat(indicadas.vencerVencidas()).isGreaterThanOrEqualTo(1);
        assertThat(repo.findById(i.getIdIndicacion()).orElseThrow().getEstado()).isEqualTo(EstadoIndicacion.VENCIDA);
    }
}
