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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.dto.NuevaCitaSesion;
import pe.consultorio.citas.dto.ParaQuien;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.MedicoTipoConsultaId;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.ReservaService;
import pe.consultorio.citas.service.disponibilidad.BloqueoTemporalService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;

/**
Nueva cita del paciente: para quién es (paso 1: para sí mismo o para otra persona), elegir médico (paso 2), elegir
horario con retención temporal (paso 4) y revisar y agendar (paso 5), con la opción de que lo llamen si se libera un
cupo antes. La cita indicada por un médico (paso 3) se conecta con las indicaciones de seguimiento.
*/
@Controller
@RequestMapping("/paciente/cita/nueva")
public class NuevaCitaController {

    static final String SESION = "nuevaCita";
    static final String PARA = "nuevaCitaPara";
    static final String PREFERENCIA = "nuevaCitaPreferencia";

    private final PortalPacienteService portal;
    private final ConfiguracionService configuracion;
    private final DisponibilidadService disponibilidad;
    private final BloqueoTemporalService bloqueos;
    private final ReservaService reserva;
    private final RegistroService registro;
    private final MedicoTipoConsultaRepository duraciones;
    private final Clock reloj;

    public NuevaCitaController(PortalPacienteService portal, ConfiguracionService configuracion,
            DisponibilidadService disponibilidad, BloqueoTemporalService bloqueos, ReservaService reserva,
            RegistroService registro, MedicoTipoConsultaRepository duraciones, Clock reloj) {
        this.portal = portal;
        this.configuracion = configuracion;
        this.disponibilidad = disponibilidad;
        this.bloqueos = bloqueos;
        this.reserva = reserva;
        this.registro = registro;
        this.duraciones = duraciones;
        this.reloj = reloj;
    }

    /** Paso 1: ¿para quién es la cita? Empieza una nueva cita desde cero. */
    @GetMapping
    public String paraQuien(@AuthenticationPrincipal UsuarioSesion usuario, HttpSession sesion, Model model) {
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PREFERENCIA);
        model.addAttribute("para", para(usuario, sesion));
        model.addAttribute("propio", usuario == null ? "" : usuario.getNombreMostrado());
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        return "paciente/nueva-cita-1";
    }

    /** «Para mí» usa el paciente propio; «Para otra persona» lo vincula a la cuenta (o lo registra) y sigue. */
    @PostMapping
    public String elegirPaciente(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(defaultValue = "MI") String paraQuien, @ModelAttribute TerceroForm tercero,
            HttpSession sesion, RedirectAttributes flash) {
        if ("OTRA".equals(paraQuien)) {
            try {
                ParaQuien p = registro.vincularTercero(usuario.getIdCuenta(), tercero);
                sesion.setAttribute(PARA, p);
                if (p.aviso() != null) {
                    flash.addFlashAttribute("aviso", p.aviso());
                }
            } catch (ReglaNegocioException e) {
                flash.addFlashAttribute("error", e.getMessage());
                flash.addFlashAttribute("tercero", tercero);
                return "redirect:/paciente/cita/nueva";
            }
        } else {
            sesion.setAttribute(PARA, propio(usuario));
        }
        return "redirect:/paciente/cita/nueva/2";
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
            int duracion = duraciones.findById(new MedicoTipoConsultaId(m.idMedico(), tipo.getIdTipoConsulta()))
                .map(MedicoTipoConsulta::getDuracionConfigurada).orElse(tipo.getDuracionDefecto());
            nc = new NuevaCitaSesion(m.idMedico(), m.nombre(), m.idEspecialidad(), m.especialidad(),
                tipo.getIdTipoConsulta(), tipo.getNombre(), duracion,
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
        model.addAttribute("para", para(usuario, sesion));
        model.addAttribute("preferencia", sesion.getAttribute(PREFERENCIA));
        model.addAttribute("segundos", bloqueos.vigenteDe(usuario.getIdCuenta())
            .map(bloqueos::segundosRestantes).orElse(0L));
        return "paciente/nueva-cita-5";
    }

    @PostMapping("/5")
    public String agendar(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(defaultValue = "false") boolean aceptaTerminos,
            @RequestParam(defaultValue = "false") boolean aceptaLlamada,
            @RequestParam(required = false) AlcancePreferencia alcance,
            @RequestParam(required = false) Integer llegada,
            @RequestParam(defaultValue = "MINUTOS") String unidadLlegada,
            @RequestParam(required = false) Integer diasPrevios, HttpSession sesion, RedirectAttributes flash) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        if (nc == null || nc.inicio() == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        PreferenciaDatos preferencia = null;
        if (aceptaLlamada) {
            if (alcance == null || llegada == null || llegada < 0 || diasPrevios == null || diasPrevios < 0) {
                flash.addFlashAttribute("error", "Completa los tres datos de la disponibilidad inmediata o desmarca la casilla.");
                return "redirect:/paciente/cita/nueva/5";
            }
            preferencia = new PreferenciaDatos(alcance, "HORAS".equals(unidadLlegada) ? llegada * 60 : llegada,
                diasPrevios);
        }
        if (!aceptaTerminos) {
            sesion.setAttribute(PREFERENCIA, preferencia);
            flash.addFlashAttribute("error", "Acepta los términos y condiciones para agendar.");
            return "redirect:/paciente/cita/nueva/5";
        }
        ParaQuien para = para(usuario, sesion);
        try {
            reserva.agendar(new NuevaCitaComando(para.idPaciente(), usuario.getIdCuenta(), nc.idMedico(),
                nc.idEspecialidad(), nc.idTipoConsulta(), nc.inicio(), nc.duracionMin(), nc.modalidad(),
                CanalOrigen.WEB, usuario.getIdCuenta(), null, preferencia, null, false));
        } catch (ReglaNegocioException e) {
            sesion.setAttribute(PREFERENCIA, preferencia);
            flash.addFlashAttribute("error", e instanceof HorarioOcupadoException
                ? "Ese horario acaba de ser ocupado. Elige otro; conservamos lo que ingresaste." : e.getMessage());
            return "redirect:/paciente/cita/nueva/4";
        }
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PARA);
        sesion.removeAttribute(PREFERENCIA);
        flash.addFlashAttribute("exito", "Cita agendada. El consultorio te llamará para confirmarla.");
        return "redirect:/paciente/citas";
    }

    /** Para quién se agenda: lo elegido en el paso 1 o, si no se eligió, el paciente propio de la cuenta. */
    private static ParaQuien para(UsuarioSesion usuario, HttpSession sesion) {
        return sesion.getAttribute(PARA) instanceof ParaQuien p ? p : propio(usuario);
    }

    private static ParaQuien propio(UsuarioSesion usuario) {
        return usuario == null ? new ParaQuien(null, "", false, null)
            : new ParaQuien(usuario.getIdPacientePropio(), usuario.getNombreMostrado(), false, null);
    }
}
