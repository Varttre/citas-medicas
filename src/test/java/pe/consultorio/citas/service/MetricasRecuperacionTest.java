package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.Metricas;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;

/** Las métricas de recuperación se llenan con horarios liberados reales y la no asistencia se desglosa. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class MetricasRecuperacionTest {

    @Autowired MetricaService metricas;
    @Autowired ExportacionService exportacion;
    @Autowired CitaEstadoService estados;
    @Autowired ReprogramacionService reprogramacion;
    @Autowired DatosPrueba datos;

    @Test
    void unHorarioLiberadoQueSeCubreCuentaComoRecuperado() {
        Medico m = datos.crearMedico("Recupera", datos.crearEspecialidad("Cardiología"));
        LocalDate manana = LocalDate.now().plusDays(1);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(m, LocalDate.now().minusDays(1), d, LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
        Cita cancelada = datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            manana.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), manana.atTime(9, 30),
            30, EstadoCita.PROGRAMADA);
        Cita lejana = datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            manana.plusDays(2).atTime(9, 0), 30, EstadoCita.PROGRAMADA);

        Metricas antes = metricas.calcular(LocalDate.now(), LocalDate.now());
        estados.cancelarPorClinica(cancelada.getIdCita(), null, "Prueba de métricas");
        reprogramacion.reprogramar(lejana.getIdCita(), manana.atTime(9, 0), null);
        Metricas despues = metricas.calcular(LocalDate.now(), LocalDate.now());

        assertThat(despues.cubiertas()).isEqualTo(antes.cubiertas() + 1);
        assertThat(despues.minutosRecuperados()).isEqualTo(antes.minutosRecuperados() + 30);
        assertThat(despues.recuperadosPct()).isNotNull();
    }

    @Test
    void laNoAsistenciaSeDesglosaPorEspecialidadYHoraYVaAlExcel() throws Exception {
        Especialidad e = datos.crearEspecialidad("Desglose");
        Medico m = datos.crearMedico("Desglose", e);
        LocalDateTime base = LocalDate.of(2025, 4, 7).atTime(9, 0);
        datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base, 30, EstadoCita.REALIZADA);
        datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base.plusMinutes(30), 30, EstadoCita.NO_ASISTIO);
        datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base.plusHours(2), 30, EstadoCita.REALIZADA);

        Metricas r = metricas.calcular(LocalDate.of(2025, 4, 7), LocalDate.of(2025, 4, 7));
        assertThat(r.porEspecialidad()).anySatisfy(g -> {
            assertThat(g.grupo()).isEqualTo(e.getNombre());
            assertThat(g.pct()).isEqualTo(33);
        });
        assertThat(r.porHora()).anySatisfy(g -> {
            assertThat(g.grupo()).isEqualTo("09:00");
            assertThat(g.pct()).isEqualTo(50);
        });
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportacion.exportar(r)))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(4);
            assertThat(libro.getSheet("Horarios recuperados").getRow(0).getCell(3).getStringCellValue())
                .isEqualTo("Minutos recuperados");
        }
    }
}
