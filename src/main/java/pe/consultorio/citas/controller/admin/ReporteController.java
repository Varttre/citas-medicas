package pe.consultorio.citas.controller.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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
import pe.consultorio.citas.dto.OpcionFiltroMedico;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.ExportacionService;
import pe.consultorio.citas.service.MetricaService;
import pe.consultorio.citas.service.PortalPacienteService;

/**
Reporte del consultorio y su descarga en Excel. Por defecto, los últimos 30 días y todo el consultorio; se filtra por
fechas, médico y especialidad antes de verlo o descargarlo, y el Excel lleva exactamente lo que se ve.
*/
@Controller
@RequestMapping("/admin/reportes")
public class ReporteController {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MetricaService metricas;
    private final ExportacionService exportacion;
    private final ConfiguracionService configuracion;
    private final PortalPacienteService portal;
    private final Clock reloj;

    public ReporteController(MetricaService metricas, ExportacionService exportacion,
            ConfiguracionService configuracion, PortalPacienteService portal, Clock reloj) {
        this.metricas = metricas;
        this.exportacion = exportacion;
        this.configuracion = configuracion;
        this.portal = portal;
        this.reloj = reloj;
    }

    @GetMapping
    public String tablero(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad,
            Model model) {
        model.addAttribute("m", calcular(desde, hasta, medico, especialidad));
        model.addAttribute("medicos", medicos());
        model.addAttribute("especialidades", configuracion.todasLasEspecialidades());
        return "admin/reportes";
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad) {
        Metricas m = calcular(desde, hasta, medico, especialidad);
        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"reporte-citas-" + m.desde() + "-" + m.hasta() + ".xlsx\"")
            .body(exportacion.exportar(m));
    }

    private Metricas calcular(LocalDate desde, LocalDate hasta, Long medico, Short especialidad) {
        LocalDate h = hasta != null ? hasta : LocalDate.now(reloj);
        LocalDate d = desde != null ? desde : h.minusDays(30);
        if (d.isAfter(h)) {
            LocalDate t = d;
            d = h;
            h = t;
        }
        return metricas.calcular(d, h, medico, especialidad);
    }

    /** Médicos activos, una vez cada uno aunque atiendan varias especialidades. */
    private List<OpcionFiltroMedico> medicos() {
        return OpcionFiltroMedico.de(portal.medicos(null));
    }
}
