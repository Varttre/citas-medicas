package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;

/** Reserva de citas: evento de creación, solapes y concurrencia resueltos por la base de datos. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class ReservaServiceTest {

    @Autowired
    ReservaService reserva;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CitaRepository citas;

    @Autowired
    CitaEventoRepository eventos;

    @Autowired
    ConfiguracionService configuracion;

    @Autowired
    JdbcTemplate jdbc;

    Medico medico;
    Especialidad especialidad;
    Short idTipo;

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Traumatología");
        medico = datos.crearMedico("Reserva", especialidad);
        idTipo = configuracion.tiposWeb().get(0).getIdTipoConsulta();
    }

    private NuevaCitaComando comando(Paciente p, LocalDateTime inicio) {
        return new NuevaCitaComando(p.getIdPaciente(), null, medico.getIdMedico(), especialidad.getIdEspecialidad(),
            idTipo, inicio, 30, Modalidad.PRESENCIAL, CanalOrigen.PRESENCIAL, null, null, null, null, false);
    }

    private LocalDateTime manana(int hora, int minuto) {
        return LocalDate.now().plusDays(3).atTime(hora, minuto);
    }

    @Test
    void agendaYRegistraElEventoDeCreacion() {
        Cita c = reserva.agendar(comando(datos.crearPaciente("81000001"), manana(9, 0)));
        assertThat(c.getIdCita()).isNotNull();
        assertThat(eventos.findAll()).anyMatch(e -> e.getCita().getIdCita().equals(c.getIdCita()));
    }

    @Test
    void unaCitaQueSeCruzaEsRechazada() {
        reserva.agendar(comando(datos.crearPaciente("81000002"), manana(10, 0)));
        assertThatThrownBy(() -> reserva.agendar(comando(datos.crearPaciente("81000003"), manana(10, 15))))
            .isInstanceOf(HorarioOcupadoException.class)
            .hasMessage("Ese horario ya fue ocupado. Elige otro.");
    }

    @Test
    void dosPersonasALaVezSoloUnaGana() throws Exception {
        Paciente a = datos.crearPaciente("81000004");
        Paciente b = datos.crearPaciente("81000005");
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<String>> resultados = new ArrayList<>();
        for (Paciente p : List.of(a, b)) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                try {
                    reserva.agendar(comando(p, manana(11, 0)));
                    return "ok";
                } catch (HorarioOcupadoException e) {
                    return "ocupado";
                }
            }));
        }
        salida.countDown();
        List<String> r = new ArrayList<>();
        for (Future<String> f : resultados) {
            r.add(f.get());
        }
        hilos.shutdown();
        assertThat(r).containsExactlyInAnyOrder("ok", "ocupado");
    }

    @Test
    void laBaseRechazaHorasFueraDeLaRejilla() {
        Paciente p = datos.crearPaciente("81000006");
        assertThatThrownBy(() -> jdbc.update("insert into cita (id_paciente, id_medico, id_especialidad, "
                + "id_tipo_consulta, inicio, fin, estado, modalidad, canal_origen, es_primera_vez, creado_en) "
                + "values (?, ?, ?, ?, ?, ?, 'PROGRAMADA', 'PRESENCIAL', 'PRESENCIAL', true, now())",
            p.getIdPaciente(), medico.getIdMedico(), especialidad.getIdEspecialidad(), idTipo,
            manana(14, 13), manana(14, 43)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
