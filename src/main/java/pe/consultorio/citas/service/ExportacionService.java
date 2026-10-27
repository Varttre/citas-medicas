package pe.consultorio.citas.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.Metricas;

/** Exporta las métricas del tablero a un libro de Excel (.xlsx) con Apache POI: una hoja por métrica. */
@Service
public class ExportacionService {

    public byte[] exportar(Metricas m) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);

            Sheet h1 = hoja(libro, "Horarios recuperados", negrita, "Cubiertos", "Expirados", "% recuperado");
            fila(h1, 1, m.cubiertas(), m.expiradas(), texto(m.recuperadosPct()));

            Sheet h2 = hoja(libro, "No asistencia", negrita, "Médico", "Especialidad", "Realizadas", "No asistió", "%");
            int i = 1;
            for (Metricas.NoAsistenciaMedico n : m.porMedico()) {
                fila(h2, i++, n.medico(), n.especialidad(), n.realizadas(), n.noAsistio(), texto(n.pct()));
            }
            fila(h2, i, "Total", "", m.realizadas(), m.noAsistio(), texto(m.noAsistenciaPct()));

            Sheet h3 = hoja(libro, "Citas indicadas", negrita, "Agendadas", "Cerradas", "% agendadas");
            fila(h3, 1, m.indicadasAgendadas(), m.indicadasCerradas(), texto(m.indicadasPct()));

            Sheet h4 = hoja(libro, "Reprogramaciones", negrita, "Citas", "Promedio", "Máximo", "Con 0", "Con 1",
                "Con 2", "Con 3 o más");
            long[] d = m.citasPorReprogramaciones();
            fila(h4, 1, m.citasConEventos(), m.reprogramacionesPromedio(), m.reprogramacionesMax(), d[0], d[1], d[2], d[3]);

            for (Sheet h : libro) {
                for (int c = 0; c < h.getRow(0).getLastCellNum(); c++) {
                    h.autoSizeColumn(c);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    private static Sheet hoja(XSSFWorkbook libro, String nombre, CellStyle negrita, String... encabezados) {
        Sheet h = libro.createSheet(nombre);
        Row r = h.createRow(0);
        for (int i = 0; i < encabezados.length; i++) {
            r.createCell(i).setCellValue(encabezados[i]);
            r.getCell(i).setCellStyle(negrita);
        }
        return h;
    }

    private static void fila(Sheet h, int numero, Object... valores) {
        Row r = h.createRow(numero);
        for (int i = 0; i < valores.length; i++) {
            Object v = valores[i];
            if (v instanceof Number n) {
                r.createCell(i).setCellValue(n.doubleValue());
            } else {
                r.createCell(i).setCellValue(String.valueOf(v));
            }
        }
    }

    private static String texto(Integer pct) {
        return pct == null ? "—" : pct + " %";
    }
}
