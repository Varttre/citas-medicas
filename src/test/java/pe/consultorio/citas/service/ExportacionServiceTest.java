package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.Metricas;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;

/** Métricas con datos conocidos y su exportación a Excel, releída con Apache POI. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class ExportacionServiceTest {

    @Autowired
    MetricaService metricas;

    @Autowired
    ExportacionService exportacion;

    @Autowired
    DatosPrueba datos;

    @Autowired
    MockMvc mvc;

    @Test
    void generaUnLibroConCuatroHojasYLosValoresEsperados() throws Exception {
        Medico m = datos.crearMedico("Métrica", datos.crearEspecialidad("Neumología"));
        LocalDateTime base = LocalDate.of(2025, 3, 3).atTime(9, 0);
        datos.crearCita(m, datos.crearPaciente("82000001"), base, 30, EstadoCita.REALIZADA);
        datos.crearCita(m, datos.crearPaciente("82000002"), base.plusHours(1), 30, EstadoCita.REALIZADA);
        datos.crearCita(m, datos.crearPaciente("82000003"), base.plusHours(2), 30, EstadoCita.REALIZADA);
        datos.crearCita(m, datos.crearPaciente("82000004"), base.plusHours(3), 30, EstadoCita.NO_ASISTIO);

        Metricas r = metricas.calcular(LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 3));
        assertThat(r.noAsistenciaPct()).isEqualTo(25);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportacion.exportar(r)))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(4);
            assertThat(libro.getSheet("No asistencia").getRow(1).getCell(4).getStringCellValue()).isEqualTo("25 %");
            assertThat(libro.getSheet("Reprogramaciones").getRow(1).getCell(0).getNumericCellValue()).isEqualTo(4.0);
        }
    }

    @Test
    void elAdministradorDescargaElExcel() throws Exception {
        mvc.perform(get("/admin/reportes/exportar").with(user("a@prueba.pe").roles("ADMINISTRADOR")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
