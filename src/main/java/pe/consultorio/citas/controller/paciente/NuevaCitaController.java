package pe.consultorio.citas.controller.paciente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.IndicacionVista;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.dto.NuevaCitaSesion;
import pe.consultorio.citas.dto.ParaQuien;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.dto.TerceroVista;
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
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.ReservaService;
import pe.consultorio.citas.service.disponibilidad.BloqueoTemporalService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;

/**
Nueva cita del paciente: para quién es (paso 1: para sí mismo, para uno de sus terceros o para una persona nueva),
elegir médico (paso 2), la cita indicada si el paciente tiene una que ese médico atiende (paso 3), elegir horario con
retención temporal (paso 4) y revisar y agendar (paso 5), con la opción de que lo llamen si se libera un cupo antes.
[Agendar seguimiento] entra con el paciente de la indicación ya fijado: la cita indicada es solo de quien la recibió.
*/
@Controller
@RequestMapping("/paciente/cita/nueva")
public class NuevaCitaController {

    static final String SESION = "nuevaCita";
    static final String PARA = "nuevaCitaPara";
    static final String PREFERENCIA = "nuevaCitaPreferencia";
    static final String SEGUIMIENTO = "nuevaCitaSeguimiento";

    private final PortalPacienteService portal;
    private final ConfiguracionService configuracion;
    private final DisponibilidadService disponibilidad;
    private final BloqueoTemporalService bloqueos;
    private final ReservaService reserva;
    private final RegistroService registro;
    private final MedicoTipoConsultaRepository duraciones;
    private final CitaIndicadaService indicadas;
    private final Clock reloj;

    public NuevaCitaController(PortalPacienteService portal, ConfiguracionService configuracion,
            DisponibilidadService disponibilidad, BloqueoTemporalService bloqueos, ReservaService reserva,
            RegistroService registro, MedicoTipoConsultaRepository duraciones, CitaIndicadaService indicadas,
            Clock reloj) {
        this.indicadas = indicadas;
        this.portal = portal;
        this.configuracion = configuracion;
        this.disponibilidad = disponibilidad;
        this.bloqueos = bloqueos;
        this.reserva = reserva;
        this.registro = registro;
        this.duraciones = duraciones;
        this.reloj = reloj;
    }

    /**
    Paso 1: ¿para quién es la cita? Empieza una nueva cita desde cero. {@code tercero}: llega desde «Mis terceros»
    con esa persona ya elegida.
    */
    @GetMapping
    public String paraQuien(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) Long tercero, HttpSession sesion, Model model) {
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PREFERENCIA);
        sesion.removeAttribute(SEGUIMIENTO);
        ParaQuien para = para(usuario, sesion);
        List<TerceroVista> terceros = usuario == null ? List.of() : registro.terceros(usuario.getIdCuenta());
        Long elegido = tercero != null ? tercero : para.tercero() ? para.idPaciente() : null;
        model.addAttribute("para", para);
        model.addAttribute("propio", usuario == null ? "" : usuario.getNombreMostrado());
        model.addAttribute("terceros", terceros);
        model.addAttribute("terceroElegido", terceros.stream().anyMatch(t -> t.idPaciente().equals(elegido))
            ? elegido : null);
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("parentescos", pe.consultorio.citas.entity.Parentesco.values());
        return "paciente/nueva-cita-1";
    }

    /**
    «Para mí» usa el paciente propio; «Para otra persona» usa uno de los terceros de la cuenta ({@code idTercero}) o,
    si elige «Otra persona» ({@code idTercero} vacío o NUEVO), lo vincula a la cuenta (o lo registra) y sigue.
    */
    @PostMapping
    public String elegirPaciente(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(defaultValue = "MI") String paraQuien, @RequestParam(required = false) String idTercero,
            @ModelAttribute TerceroForm tercero, HttpSession sesion, RedirectAttributes flash) {
        if ("OTRA".equals(paraQuien)) {
            try {
                if ((idTercero == null || idTercero.isBlank()) && !registro.terceros(usuario.getIdCuenta()).isEmpty()) {
                    // Con terceros guardados hay que marcar uno (o «Otra persona»): nunca se elige uno por defecto.
                    throw new ReglaNegocioException("Elige para cuál de tus terceros es la cita, o «Otra persona».");
                }
                ParaQuien p = idTercero != null && idTercero.matches("\\d+")
                    ? registro.terceroVinculado(usuario.getIdCuenta(), Long.valueOf(idTercero))
                    : registro.vincularTercero(usuario.getIdCuenta(), tercero);
                if (!p.tercero()) {
                    throw new ReglaNegocioException(RegistroService.ES_TU_DOCUMENTO);
                }
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

    /**
    [Agendar seguimiento] desde el inicio o «Mis citas»: fija al paciente de la indicación (sin «Para otra persona»)
    y pasa a elegir uno de los médicos que atienden el tipo indicado.
    */
    @GetMapping("/seguimiento/{id}")
    public String seguimiento(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            HttpSession sesion, RedirectAttributes flash) {
        IndicacionVista ind;
        try {
            ind = indicadas.pendienteDeCuenta(usuario.getIdCuenta(), id);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/paciente/inicio";
        }
        bloqueos.liberar(usuario.getIdCuenta());
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PREFERENCIA);
        boolean tercero = !ind.idPaciente().equals(usuario.getIdPacientePropio());
        sesion.setAttribute(PARA, new ParaQuien(ind.idPaciente(), ind.paciente(), tercero, null));
        sesion.setAttribute(SEGUIMIENTO, ind.idIndicacion());
        return "redirect:/paciente/cita/nueva/2";
    }

    /** Paso 2: elegir médico, filtrando por especialidad. En un seguimiento, solo los que atienden el tipo indicado. */
    @GetMapping("/2")
    public String elegirMedico(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) Short especialidad, HttpSession sesion, Model model) {
        IndicacionVista seguimiento = seguimiento(usuario, sesion);
        model.addAttribute("especialidades", configuracion.especialidadesActivas());
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("medicos", portal.medicos(especialidad).stream()
            .filter(m -> seguimiento == null || indicadas.medicoAtiende(m.idMedico(), seguimiento.idTipoConsulta()))
            .toList());
        model.addAttribute("seguimiento", seguimiento);
        model.addAttribute("para", para(usuario, sesion));
        return "paciente/nueva-cita-2";
    }

    /**
    Paso 3: si el paciente elegido tiene una cita indicada pendiente y vigente que ese médico atiende, se ofrece
    agendarla (tipo y duración de la indicación) o pedir una consulta nueva. Si no tiene, sigue directo a horarios.
    En un seguimiento ya está decidido: va a horarios con la indicación.
    */
    @GetMapping("/3")
    public String citaIndicada(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad,
            HttpSession sesion, Model model) {
        if (medico == null || especialidad == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        String horarios = "redirect:/paciente/cita/nueva/4?medico=" + medico + "&especialidad=" + especialidad;
        IndicacionVista seguimiento = seguimiento(usuario, sesion);
        if (seguimiento != null) {
            return horarios + "&indicacion=" + seguimiento.idIndicacion();
        }
        ParaQuien para = para(usuario, sesion);
        IndicacionVista ind = para.idPaciente() == null ? null
            : indicadas.pendienteDePacienteParaMedico(para.idPaciente(), medico)
                .map(i -> indicadas.vista(i.getIdIndicacion())).orElse(null);
        if (ind == null) {
            return horarios;
        }
        MedicoOpcion m = portal.medicos(especialidad).stream().filter(x -> x.idMedico().equals(medico)).findFirst()
            .orElseThrow(() -> new ReglaNegocioException("Ese médico no atiende la especialidad elegida."));
        model.addAttribute("ind", ind);
        model.addAttribute("medico", m);
        model.addAttribute("para", para);
        return "paciente/nueva-cita-3";
    }

    /** Paso 4: horarios del médico para el tipo de consulta web por defecto. */
    @GetMapping("/4")
    public String elegirHorario(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) Long medico, @RequestParam(required = false) Short especialidad,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) Long indicacion, HttpSession sesion, Model model,
            RedirectAttributes flash) {
        NuevaCitaSesion nc = (NuevaCitaSesion) sesion.getAttribute(SESION);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate desdeIndicada = null;
        if (medico != null && especialidad != null) {
            MedicoOpcion m = portal.medicos(especialidad).stream()
                .filter(x -> x.idMedico().equals(medico)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese médico no atiende la especialidad elegida."));
            Modalidad mod = modalidad != null ? modalidad : Modalidad.PRESENCIAL;
            if (indicacion != null) {
                IndicacionVista ind;
                try {
                    ind = indicacionPara(usuario, sesion, indicacion, m.idMedico());
                } catch (ReglaNegocioException e) {
                    flash.addFlashAttribute("error", e.getMessage());
                    return "redirect:/paciente/cita/nueva/2";
                }
                nc = new NuevaCitaSesion(m.idMedico(), m.nombre(), m.idEspecialidad(), m.especialidad(),
                    ind.idTipoConsulta(), ind.tipo(), ind.duracion(), mod, null, ind.idIndicacion());
                desdeIndicada = ind.fechaRecomendada();
            } else {
                Short idTipoWeb = configuracion.parametros().getTipoWebDefecto().getIdTipoConsulta();
                TipoConsulta tipo = configuracion.tiposWeb().stream()
                    .filter(t -> t.getIdTipoConsulta().equals(idTipoWeb)).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException("No hay un tipo de consulta disponible por la web."));
                int duracion = duraciones.findById(new MedicoTipoConsultaId(m.idMedico(), tipo.getIdTipoConsulta()))
                    .map(MedicoTipoConsulta::getDuracionConfigurada).orElse(tipo.getDuracionDefecto());
                nc = new NuevaCitaSesion(m.idMedico(), m.nombre(), m.idEspecialidad(), m.especialidad(),
                    tipo.getIdTipoConsulta(), tipo.getNombre(), duracion, mod, null, null);
            }
        } else if (nc != null && modalidad != null) {
            nc = nc.conModalidad(modalidad);
        }
        if (nc == null) {
            return "redirect:/paciente/cita/nueva/2";
        }
        sesion.setAttribute(SESION, nc);
        // Una cita indicada se busca desde su fecha recomendada, si aún no llega.
        LocalDate fecha = desde != null ? desde
            : desdeIndicada != null && desdeIndicada.isAfter(hoy) ? desdeIndicada : hoy;
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
        // «Acepto ser llamado» solo si hay la anticipación mínima hasta la cita (RC-046); los días, hasta los que faltan.
        LocalDateTime ahora = LocalDateTime.now(reloj);
        model.addAttribute("elegible", configuracion.elegibleAdelanto(ahora, nc.inicio()));
        model.addAttribute("maxDias", java.time.temporal.ChronoUnit.DAYS.between(ahora.toLocalDate(),
            nc.inicio().toLocalDate()));
        model.addAttribute("minHoras", configuracion.parametros().getMinHorasAdelanto());
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
            if (!configuracion.elegibleAdelanto(LocalDateTime.now(reloj), nc.inicio())) {
                flash.addFlashAttribute("error", PreferenciaService.NO_ELEGIBLE + " Desmarca la casilla para agendar.");
                return "redirect:/paciente/cita/nueva/5";
            }
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
                CanalOrigen.WEB, usuario.getIdCuenta(), nc.idIndicacion(), preferencia, null, false));
        } catch (ReglaNegocioException e) {
            sesion.setAttribute(PREFERENCIA, preferencia);
            flash.addFlashAttribute("error", e instanceof HorarioOcupadoException
                ? "Ese horario acaba de ser ocupado. Elige otro; conservamos lo que ingresaste." : e.getMessage());
            return "redirect:/paciente/cita/nueva/4";
        }
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PARA);
        sesion.removeAttribute(PREFERENCIA);
        sesion.removeAttribute(SEGUIMIENTO);
        flash.addFlashAttribute("exito", nc.idIndicacion() != null
            ? "Cita indicada agendada. El consultorio te llamará para confirmarla."
            : "Cita agendada. El consultorio te llamará para confirmarla.");
        return "redirect:/paciente/citas";
    }

    /** «Cancelar» (desde cualquier paso, tras confirmar): suelta el horario retenido y olvida lo elegido. */
    @PostMapping("/cancelar")
    public String cancelar(@AuthenticationPrincipal UsuarioSesion usuario, HttpSession sesion, RedirectAttributes flash) {
        bloqueos.liberar(usuario.getIdCuenta());
        sesion.removeAttribute(SESION);
        sesion.removeAttribute(PARA);
        sesion.removeAttribute(PREFERENCIA);
        sesion.removeAttribute(SEGUIMIENTO);
        flash.addFlashAttribute("aviso", "Cancelaste la nueva cita. No se agendó nada.");
        return "redirect:/paciente/inicio";
    }

    /** La indicación del seguimiento en curso (entrada desde [Agendar seguimiento]), si sigue pendiente; si no, null. */
    private IndicacionVista seguimiento(UsuarioSesion usuario, HttpSession sesion) {
        if (!(sesion.getAttribute(SEGUIMIENTO) instanceof Long id) || usuario == null) {
            return null;
        }
        try {
            return indicadas.pendienteDeCuenta(usuario.getIdCuenta(), id);
        } catch (ReglaNegocioException e) {
            sesion.removeAttribute(SEGUIMIENTO);
            return null;
        }
    }

    /**
    La indicación que se quiere agendar, validada: pendiente y vigente, de un paciente de la cuenta, del mismo
    paciente para quien se agenda (la cita indicada es solo de quien la recibió) y de un tipo que el médico atiende.
    */
    private IndicacionVista indicacionPara(UsuarioSesion usuario, HttpSession sesion, Long idIndicacion, Long idMedico) {
        IndicacionVista ind = indicadas.pendienteDeCuenta(usuario.getIdCuenta(), idIndicacion);
        if (!ind.idPaciente().equals(para(usuario, sesion).idPaciente())) {
            throw new ReglaNegocioException("La cita indicada es solo para el paciente que la recibió.");
        }
        if (!indicadas.medicoAtiende(idMedico, ind.idTipoConsulta())) {
            throw new ReglaNegocioException("Ese médico no atiende el tipo de consulta indicado. Elige otro médico.");
        }
        return ind;
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
