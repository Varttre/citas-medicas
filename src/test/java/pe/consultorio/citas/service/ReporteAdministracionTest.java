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

/**
Bloque 4: lo recuperado se mide contra el horario que realmente se soltó (no contra todo el bloque libre que se
ofreció) y el reporte del administrador se filtra por médico y especialidad.
*/
@SpringBootTest
@Import(PostgresTestConfig.class)
class ReporteAdministracionTest {

    @Autowired MetricaService metricas;
    @Autowired ExportacionService exportacion;
    @Autowired CitaEstadoService estados;
    @Autowired ReprogramacionService reprogramacion;
    @Autowired DatosPrueba datos;

    private Medico medicoDeNueveADiez(String nombre) {
        Medico m = datos.crearMedico(nombre, datos.crearEspecialidad("Recupera"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(m, LocalDate.now().minusDays(1), d, LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
        return m;
    }

    private Cita cita(Medico m, LocalDateTime inicio, int minutos) {
        return datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), inicio, minutos,
            EstadoCita.PROGRAMADA);
    }

    /** PA-155 ★ */
    @Test
    void soloCuentaLoQueCaeDentroDelHorarioSoltado() {
        Medico m = medicoDeNueveADiez("SinInflar");
        LocalDate manana = LocalDate.now().plusDays(1);
        // Se cancela 09:15–09:45; el bloque que se ofrece es 09:00–10:00 (lo de alrededor ya estaba libre).
        Cita cancelada = cita(m, manana.atTime(9, 15), 30);
        Cita larga = cita(m, manana.plusDays(2).atTime(9, 0), 60);

        Metricas antes = metricas.calcular(LocalDate.now(), LocalDate.now(), m.getIdMedico(), null);
        estados.cancelarPorClinica(cancelada.getIdCita(), null, "Prueba sin inflar");
        reprogramacion.reprogramar(larga.getIdCita(), manana.atTime(9, 0), null);
        Metricas despues = metricas.calcular(LocalDate.now(), LocalDate.now(), m.getIdMedico(), null);

        // Antes se contaban 60 minutos (todo el bloque); el horario soltado era de 30.
        assertThat(despues.minutosRecuperados() - antes.minutosRecuperados()).isEqualTo(30);
        assertThat(despues.horariosRecuperados() - antes.horariosRecuperados()).isEqualTo(1);
        assertThat(despues.cubiertas() - antes.cubiertas()).isEqualTo(1);
    }

    /** PA-156 */
    @Test
    void dosCancelacionesSeguidasCubiertasDeUnaVezSonDosHorariosRecuperados() {
        Medico m = medicoDeNueveADiez("DosSeguidas");
        LocalDate manana = LocalDate.now().plusDays(1);
        Cita a = cita(m, manana.atTime(9, 0), 30);
        Cita b = cita(m, manana.atTime(9, 30), 30);
        Cita larga = cita(m, manana.plusDays(2).atTime(9, 0), 60);

        estados.cancelarPorClinica(a.getIdCita(), null, "Primera");
        estados.cancelarPorClinica(b.getIdCita(), null, "Segunda: el bloque se une con la primera");
        reprogramacion.reprogramar(larga.getIdCita(), manana.atTime(9, 0), null);
        Metricas r = metricas.calcular(LocalDate.now(), LocalDate.now(), m.getIdMedico(), null);

        assertThat(r.minutosRecuperados()).isEqualTo(60);
        assertThat(r.horariosRecuperados()).isEqualTo(2);
        // Los dos cancelados y el horario que dejó libre la cita movida.
        assertThat(r.horariosSoltados()).isEqualTo(3);
        assertThat(r.detalleMedicos()).singleElement().satisfies(d -> {
            assertThat(d.horariosRecuperados()).isEqualTo(2);
            assertThat(d.minutosRecuperados()).isEqualTo(60);
        });
    }

    /** PA-157 ★ */
    @Test
    void elReporteSeFiltraPorMedicoYEspecialidadYElExcelLlevaLoMismo() throws Exception {
        Medico uno = datos.crearMedico("FiltroUno", datos.crearEspecialidad("FiltroA"));
        Especialidad b = datos.crearEspecialidad("FiltroB");
        Medico otro = datos.crearMedico("FiltroOtro", b);
        LocalDateTime base = LocalDate.of(2025, 5, 12).atTime(9, 0);
        datos.crearCita(uno, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base, 30,
            EstadoCita.REALIZADA);
        datos.crearCita(uno, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base.plusHours(1), 30,
            EstadoCita.CANCELADA_POR_PACIENTE);
        datos.crearCita(otro, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), base, 30,
            EstadoCita.NO_ASISTIO);
        LocalDate dia = base.toLocalDate();

        Metricas soloUno = metricas.calcular(dia, dia, uno.getIdMedico(), null);
        assertThat(soloUno.resumen().total()).isEqualTo(2);
        assertThat(soloUno.resumen().realizadas()).isEqualTo(1);
        assertThat(soloUno.resumen().canceladasPaciente()).isEqualTo(1);
        assertThat(soloUno.resumen().noAsistio()).isZero();
        assertThat(soloUno.detalleMedicos()).singleElement()
            .satisfies(d -> assertThat(d.medico()).isEqualTo("FiltroUno Prueba"));
        assertThat(soloUno.descripcionFiltro()).startsWith("FiltroUno Prueba");

        Metricas porEspecialidad = metricas.calcular(dia, dia, null, b.getIdEspecialidad());
        assertThat(porEspecialidad.resumen().total()).isEqualTo(1);
        assertThat(porEspecialidad.resumen().noAsistio()).isEqualTo(1);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportacion.exportar(soloUno)))) {
            assertThat(libro.getSheet("Resumen").getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("FiltroUno Prueba");
            assertThat(libro.getSheet("Por médico").getLastRowNum()).isEqualTo(1);
            assertThat(libro.getSheet("Por médico").getRow(1).getCell(2).getNumericCellValue()).isEqualTo(2.0);
        }
    }
}
