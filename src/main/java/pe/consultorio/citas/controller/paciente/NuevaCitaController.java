package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.dto.NuevaCitaSesion;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.ReservaService;
import pe.consultorio.citas.service.disponibilidad.BloqueoTemporalService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;

/**
Nueva cita del paciente para sí mismo: elegir médico (paso 2), elegir horario con retención de 5 minutos (paso 4) y
confirmar (paso 5). Lo elegido se guarda en la sesión entre pasos.
*/
@Controller
@RequestMapping("/paciente/cita/nueva")
public class NuevaCitaController {

    static final String SESION = "nuevaCita";

    private final PortalPacienteService portal;
    private final ConfiguracionService configuracion;
    private final DisponibilidadService disponibilidad;
    private final BloqueoTemporalService bloqueos;
    private final ReservaService reserva;
    private final Clock reloj;

    public NuevaCitaController(PortalPacienteService portal, ConfiguracionService configuracion,
            DisponibilidadService disponibilidad, BloqueoTemporalService bloqueos, ReservaService reserva,
            Clock reloj) {
        this.portal = portal;
        this.configuracion = configuracion;
        this.disponibilidad = disponibilidad;
        this.bloqueos = bloqueos;
        this.reserva = reserva;
        this.reloj = reloj;
    }

    /** Paso 2: elegir médico, filtrando por especialidad. */
    @GetMapping("/2")
    public String elegirMedico(@RequestParam(required = false) Short especialidad, Model model) {
        model.addAttribute("especialidades", configuracion.especialidadesActivas());
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("medicos", portal.medicos(especialidad));
        return "paciente/nueva-cita-2";
    }

    /** Paso 4: horarios del médico para el tipo de consulta web por defecto. */
    @GetMapping("/4")
    public String elegirHorario(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            HttpSession sesion, Model model) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        if (medico != null && especialidad != null) {
            MedicoOpcion m = portal.medicos(especialidad).stream()
                .filter(x -> x.idMedico().equals(medico)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese médico no atiende la especialidad elegida."));
            Short idTipoWeb = configuracion.parametros().getTipoWebDefecto().getIdTipoConsulta();
            TipoConsulta tipo = configuracion.tiposWeb().stream()
                .filter(t -> t.getIdTipoConsulta().equals(idTipoWeb)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("No hay un tipo de consulta disponible por la web."));
            nc = new NuevaCitaSesion(m.idMedico(), m.nombre(), m.idEspecialidad(), m.especialidad(),
                tipo.getIdTipoConsulta(), tipo.getNombre(), tipo.getDuracionDefecto(),
                modalidad != null ? modalidad : Modalidad.PRESENCIAL, null);
        } else if (nc != null && modalidad != null) {
            nc = new NuevaCitaSesion(nc.idMedico(), nc.medico(), nc.idEspecialidad(), nc.especialidad(),
                nc.idTipoConsulta(), nc.tipo(), nc.duracionMin(), modalidad, nc.inicio());
        }
        if (nc == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        sesion.setAttribute(SESION, nc);
        LocalDate fecha = desde != null ? desde : LocalDate.now(reloj);
        model.addAttribute("nc", nc);
        model.addAttribute("desde", fecha);
        model.addAttribute("dias", disponibilidad.ofertasProximas(nc.idMedico(), nc.duracionMin(), fecha,
            usuario.getIdCuenta()));
        return "paciente/nueva-cita-4";
    }

    /** Retiene el horario elegido 5 minutos y pasa al resumen. */
    @PostMapping("/4")
    public String tomarHorario(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inicio,
            HttpSession sesion, RedirectAttributes flash) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        if (nc == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        try {
            bloqueos.tomar(usuario.getIdCuenta(), nc.idMedico(), inicio, inicio.plusMinutes(nc.duracionMin()));
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/paciente/cita/nueva/4";
        }
        sesion.setAttribute(SESION, nc.conHorario(inicio));
        return "redirect:/paciente/cita/nueva/5";
    }

    /** Paso 5: resumen con el tiempo que queda de la retención. */
    @GetMapping("/5")
    public String resumen(@AuthenticationPrincipal UsuarioSesion usuario, HttpSession sesion, Model model) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        if (nc == null || nc.inicio() == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        model.addAttribute("nc", nc);
        model.addAttribute("segundos", bloqueos.vigenteDe(usuario.getIdCuenta())
            .map(bloqueos::segundosRestantes).orElse(0L));
        return "paciente/nueva-cita-5";
    }

    @PostMapping("/5")
    public String agendar(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(defaultValue = "false") boolean aceptaTerminos, HttpSession sesion,
            RedirectAttributes flash) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        if (nc == null || nc.inicio() == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        if (!aceptaTerminos) {
            flash.addFlashAttribute("error", "Acepta los términos y condiciones para agendar.");
            return "redirect:/paciente/cita/nueva/5";
        }
        try {
            reserva.agendar(new NuevaCitaComando(usuario.getIdPacientePropio(), usuario.getIdCuenta(), nc.idMedico(),
                nc.idEspecialidad(), nc.idTipoConsulta(), nc.inicio(), nc.duracionMin(), nc.modalidad(),
                CanalOrigen.WEB, usuario.getIdCuenta(), null, null, null, false));
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/paciente/cita/nueva/4";
        }
        sesion.removeAttribute(SESION);
        flash.addFlashAttribute("exito", "Cita agendada. El consultorio te llamará para confirmarla.");
        return "redirect:/paciente/citas";
    }
}
