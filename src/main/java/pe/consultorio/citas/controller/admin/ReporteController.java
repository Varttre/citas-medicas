package pe.consultorio.citas.controller.admin;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pe.consultorio.citas.dto.Metricas;
import pe.consultorio.citas.service.ExportacionService;
import pe.consultorio.citas.service.MetricaService;

/** Tablero de métricas del consultorio y su descarga en Excel. Por defecto, los últimos 30 días. */
@Controller
@RequestMapping("/admin/reportes")
public class ReporteController {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MetricaService metricas;
    private final ExportacionService exportacion;
    private final Clock reloj;

    public ReporteController(MetricaService metricas, ExportacionService exportacion, Clock reloj) {
        this.metricas = metricas;
        this.exportacion = exportacion;
        this.reloj = reloj;
    }

    @GetMapping
    public String tablero(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Model model) {
        model.addAttribute("m", calcular(desde, hasta));
        return "admin/reportes";
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Metricas m = calcular(desde, hasta);
        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"reporte-citas-" + m.desde() + "-" + m.hasta() + ".xlsx\"")
            .body(exportacion.exportar(m));
    }

    private Metricas calcular(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta != null ? hasta : LocalDate.now(reloj);
        LocalDate d = desde != null ? desde : h.minusDays(30);
        return metricas.calcular(d, h);
    }
}
