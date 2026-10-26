package pe.consultorio.citas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.CitaRepository;

/**
Comprueba que las entidades coinciden con el esquema creado por Flyway. Si un tipo o un nombre de columna no
coincide, Hibernate no deja arrancar el contexto (ddl-auto=validate) y estas pruebas fallan.
*/
@SpringBootTest
@Import(PostgresTestConfig.class)
class EsquemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CitaRepository citas;

    @Test
    void elContextoArrancaYExisteLaRestriccionContraSolapes() {
        Integer n = jdbc.queryForObject(
            "select count(*) from pg_constraint where conname = 'excl_cita_solape'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void unaCitaSeGuardaYSeLeeConSusRelaciones() {
        Especialidad e = datos.crearEspecialidad("Medicina general");
        Medico m = datos.crearMedico("Ana", e);
        Paciente p = datos.crearPaciente("40000001");
        LocalDateTime inicio = LocalDateTime.of(2030, 3, 4, 9, 0);

        Cita c = datos.crearCita(m, p, inicio, 30, EstadoCita.PROGRAMADA);

        Cita leida = citas.findById(c.getIdCita()).orElseThrow();
        assertThat(leida.getInicio()).isEqualTo(inicio);
        assertThat(leida.getFin()).isEqualTo(inicio.plusMinutes(30));
        assertThat(leida.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void laBaseRechazaDosCitasSolapadasDelMismoMedico() {
        Especialidad e = datos.crearEspecialidad("Pediatría");
        Medico m = datos.crearMedico("Luis", e);
        Paciente p1 = datos.crearPaciente("40000002");
        Paciente p2 = datos.crearPaciente("40000003");
        datos.crearCita(m, p1, LocalDateTime.of(2030, 3, 5, 10, 0), 30, EstadoCita.PROGRAMADA);

        assertThatThrownBy(() ->
            datos.crearCita(m, p2, LocalDateTime.of(2030, 3, 5, 10, 15), 30, EstadoCita.PROGRAMADA))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaVersionDeHorarioGuardaSusFranjas() {
        Medico m = datos.crearMedico("Rosa", datos.crearEspecialidad("Cardiología"));
        LocalDate desde = LocalDate.of(2030, 1, 1);

        datos.crearHorario(m, desde, 1, LocalTime.of(8, 0), LocalTime.of(12, 0));
        datos.crearHorario(m, desde, 3, LocalTime.of(14, 0), LocalTime.of(18, 0));

        Integer franjas = jdbc.queryForObject(
            "select count(*) from franja_horaria f join horario_version v using (id_horario_version) "
                + "where v.id_medico = ?", Integer.class, m.getIdMedico());
        assertThat(franjas).isEqualTo(2);
    }
}
