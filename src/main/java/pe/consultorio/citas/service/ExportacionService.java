package pe.consultorio.citas.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.Metricas;

/**
Exporta el reporte del consultorio a un libro de Excel (.xlsx) con Apache POI: el resumen con los filtros aplicados,
una hoja por métrica y el detalle por médico.
*/
@Service
public class ExportacionService {

    public byte[] exportar(Metricas m) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);

            Sheet h0 = hoja(libro, "Resumen", negrita, "Dato", "Valor");
            Metricas.ResumenCitas r = m.resumen();
            Object[][] resumen = {
                {"Desde", m.desde().toString()}, {"Hasta", m.hasta().toString()},
                {"Médico", m.filtroMedico() != null ? m.filtroMedico() : "Todos"},
                {"Especialidad", m.filtroEspecialidad() != null ? m.filtroEspecialidad() : "Todas"},
                {"Citas", r.total()}, {"Realizadas", r.realizadas()}, {"No asistió", r.noAsistio()},
                {"Canceló el paciente", r.canceladasPaciente()}, {"Canceló el consultorio", r.canceladasClinica()},
                {"Reprogramadas (al menos una vez)", r.reprogramadas()}, {"Por atender", r.porAtender()},
                {"En atención", r.enAtencion()},
                {"Horarios soltados", m.horariosSoltados()}, {"Horarios recuperados", m.horariosRecuperados()},
                {"Minutos soltados", m.minutosSoltados()}, {"Minutos recuperados", m.minutosRecuperados()}};
            for (int k = 0; k < resumen.length; k++) {
                fila(h0, k + 1, resumen[k]);
            }

            Sheet h1 = hoja(libro, "Horarios recuperados", negrita, "Cubiertos", "Expirados", "% recuperado",
                "Minutos recuperados", "Horarios recuperados", "Horarios soltados", "Minutos soltados",
                "Abiertos ahora");
            fila(h1, 1, m.cubiertas(), m.expiradas(), texto(m.recuperadosPct()), m.minutosRecuperados(),
                m.horariosRecuperados(), m.horariosSoltados(), m.minutosSoltados(), m.abiertasAhora());

            Sheet h2 = hoja(libro, "No asistencia", negrita, "Médico", "Especialidad", "Realizadas", "No asistió", "%");
            int i = 1;
            for (Metricas.NoAsistenciaMedico n : m.porMedico()) {
                fila(h2, i++, n.medico(), n.especialidad(), n.realizadas(), n.noAsistio(), texto(n.pct()));
            }
            fila(h2, i++, "Total", "", m.realizadas(), m.noAsistio(), texto(m.noAsistenciaPct()));
            i = grupos(h2, i + 1, "Por especialidad", m.porEspecialidad(), negrita);
            grupos(h2, i + 1, "Por hora de inicio", m.porHora(), negrita);

            Sheet h3 = hoja(libro, "Citas indicadas", negrita, "Agendadas", "Cerradas", "% agendadas");
            fila(h3, 1, m.indicadasAgendadas(), m.indicadasCerradas(), texto(m.indicadasPct()));

            Sheet h4 = hoja(libro, "Reprogramaciones", negrita, "Citas", "Promedio", "Máximo", "Con 0", "Con 1",
                "Con 2", "Con 3 o más");
            long[] d = m.citasPorReprogramaciones();
            fila(h4, 1, m.citasConEventos(), m.reprogramacionesPromedio(), m.reprogramacionesMax(), d[0], d[1], d[2], d[3]);

            Sheet h5 = hoja(libro, "Por médico", negrita, "Médico", "Especialidad", "Citas", "Realizadas",
                "No asistió", "Canceló el paciente", "Canceló el consultorio", "Reprogramadas", "Por atender",
                "Horarios recuperados", "Minutos recuperados");
            i = 1;
            for (Metricas.DetalleMedico x : m.detalleMedicos()) {
                Metricas.ResumenCitas y = x.resumen();
                fila(h5, i++, x.medico(), x.especialidades(), y.total(), y.realizadas(), y.noAsistio(),
                    y.canceladasPaciente(), y.canceladasClinica(), y.reprogramadas(), y.porAtender() + y.enAtencion(),
                    x.horariosRecuperados(), x.minutosRecuperados());
            }

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

    /** Una tabla de no asistencia agrupada debajo de la principal; devuelve la siguiente fila libre. */
    private static int grupos(Sheet h, int desde, String titulo, List<Metricas.NoAsistenciaGrupo> grupos,
            CellStyle negrita) {
        Row r = h.createRow(desde);
        String[] encabezados = {titulo, "", "Realizadas", "No asistió", "%"};
        for (int c = 0; c < encabezados.length; c++) {
            r.createCell(c).setCellValue(encabezados[c]);
            r.getCell(c).setCellStyle(negrita);
        }
        int i = desde + 1;
        for (Metricas.NoAsistenciaGrupo g : grupos) {
            fila(h, i++, g.grupo(), "", g.realizadas(), g.noAsistio(), texto(g.pct()));
        }
        return i;
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
