package pe.consultorio.citas.controller.recepcion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.IndicacionVista;
import pe.consultorio.citas.dto.MedicoOpcion;
import pe.consultorio.citas.dto.OpcionTitular;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.service.CitaIndicadaService;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.PacienteService;
import pe.consultorio.citas.service.PortalPacienteService;
import pe.consultorio.citas.service.ReservaService;
import pe.consultorio.citas.service.TableroRecepcionService;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;
import pe.consultorio.citas.service.disponibilidad.Oferta;
import pe.consultorio.citas.service.disponibilidad.OfertaDia;

/**
Pantallas de recepción (también las usa el administrador): el inicio con el trabajo pendiente, la nueva cita desde
recepción (por teléfono, presencial, WhatsApp o derivación) y las citas indicadas por el médico pendientes por
agendar. Las colas, la agenda y los pacientes tienen su propio controlador.
*/
@Controller
@RequestMapping("/recepcion")
public class RecepcionController {

    private final TableroRecepcionService tablero;
    private final PacienteService pacientes;
    private final PortalPacienteService portal;
    private final ConfiguracionService configuracion;
    private final MedicoTipoConsultaRepository duraciones;
    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    private final CitaIndicadaService indicadas;
    private final Clock reloj;

    public RecepcionController(TableroRecepcionService tablero, PacienteService pacientes, PortalPacienteService portal,
            ConfiguracionService configuracion, MedicoTipoConsultaRepository duraciones,
            DisponibilidadService disponibilidad, ReservaService reservas, CitaIndicadaService indicadas,
            Clock reloj) {
        this.tablero = tablero;
        this.pacientes = pacientes;
        this.portal = portal;
        this.configuracion = configuracion;
        this.duraciones = duraciones;
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.indicadas = indicadas;
        this.reloj = reloj;
    }

    /** Trabajo pendiente: seis contadores enlazados a su pantalla y cómo va cada médico hoy. */
    @GetMapping("/inicio")
    public String inicio(Model model) {
        model.addAttribute("t", tablero.tablero());
        return "recepcion/inicio";
    }

    /**
    Nueva cita en una sola pantalla que se va completando: paciente (y la cuenta titular, que es el contacto), canal,
    tipo y modalidad, especialidad y médico, y horario. Si viene de una cita indicada, el paciente y el tipo quedan
    fijados por la indicación; si el paciente tiene indicaciones pendientes, se ofrecen. {@code titular}: id de la
    cuenta titular, «0» sin titular; si no llega, la cuenta propia del paciente (si tiene).
    */
    @GetMapping("/cita/nueva")
    public String nuevaCita(@AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String q, @RequestParam(required = false) Long idPaciente,
            @RequestParam(required = false) CanalOrigen canal, @RequestParam(required = false) Short idTipo,
            @RequestParam(required = false) Modalidad modalidad, @RequestParam(required = false) Short idEspecialidad,
            @RequestParam(required = false) Long idMedico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) Long indicacion, @RequestParam(required = false) String titular,
            Model model) {
        IndicacionVista ind = indicacion == null ? null : indicadas.vista(indicacion);
        if (ind != null && !"PENDIENTE".equals(ind.estado())) {
            model.addAttribute("aviso", "Esa cita indicada ya no está pendiente; puedes agendar una consulta nueva.");
            ind = null;
            indicacion = null;
        }
        if (ind != null) {
            idPaciente = ind.idPaciente();
            idTipo = ind.idTipoConsulta();
            model.addAttribute("indicacion", ind);
        }
        model.addAttribute("q", q);
        model.addAttribute("resultados", idPaciente == null ? pacientes.buscar(q) : List.of());
        model.addAttribute("paciente", idPaciente == null ? null : pacientes.ficha(idPaciente));
        model.addAttribute("idPaciente", idPaciente);
        List<OpcionTitular> titulares = idPaciente == null ? List.of() : pacientes.titulares(idPaciente);
        Long idTitular = elegirTitular(titulares, titular);
        model.addAttribute("titulares", titulares);
        model.addAttribute("titular", idTitular == null ? "0" : idTitular.toString());
        model.addAttribute("indicadasPaciente", idPaciente == null || ind != null ? List.of()
            : indicadas.pendientesVistaDePaciente(idPaciente));
        model.addAttribute("canal", canal == null ? CanalOrigen.TELEFONO : canal);
        model.addAttribute("canales", List.of(CanalOrigen.TELEFONO, CanalOrigen.PRESENCIAL, CanalOrigen.WHATSAPP,
            CanalOrigen.DERIVACION));
        model.addAttribute("tipos", configuracion.tiposActivos());
        model.addAttribute("idTipo", idTipo);
        model.addAttribute("modalidad", modalidad == null ? Modalidad.PRESENCIAL : modalidad);
        model.addAttribute("especialidades", configuracion.especialidadesActivas());
        model.addAttribute("idEspecialidad", idEspecialidad);
        final IndicacionVista indFinal = ind;
        List<MedicoOpcion> medicos = portal.medicos(idEspecialidad).stream()
            .filter(m -> indFinal == null || indicadas.medicoAtiende(m.idMedico(), indFinal.idTipoConsulta()))
            .toList();
        model.addAttribute("medicos", medicos);
        model.addAttribute("idMedico", idMedico);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate dia = desde != null ? desde
            : ind != null && ind.fechaRecomendada().isAfter(hoy) ? ind.fechaRecomendada() : hoy;
        model.addAttribute("desde", dia);

        Integer duracion = null;
        if (idMedico != null && idTipo != null) {
            final Short tipo = idTipo;
            duracion = ind != null ? Integer.valueOf(ind.duracion())
                : duraciones.delMedico(idMedico).stream()
                    .filter(mt -> mt.getTipoConsulta().getIdTipoConsulta().equals(tipo))
                    .map(mt -> (int) mt.getDuracionConfigurada())
                    .findFirst().orElse(null);
            if (duracion == null) {
                model.addAttribute("aviso", "Ese médico no atiende ese tipo de consulta.");
            } else {
                List<OfertaDia> porCalce = disponibilidad.ofertasProximas(idMedico, duracion, dia,
                    usuario.getIdCuenta());
                // El motor ordena por mejor calce; en pantalla van por hora y el mejor calce de cada día se marca.
                model.addAttribute("recomendados", porCalce.stream()
                    .map(d -> d.ofertas().get(0).inicio()).collect(java.util.stream.Collectors.toSet()));
                model.addAttribute("ofertas", porCalce.stream()
                    .map(d -> new OfertaDia(d.fecha(), d.ofertas().stream()
                        .sorted(java.util.Comparator.comparing(Oferta::inicio)).toList()))
                    .toList());
            }
        }
        model.addAttribute("duracion", duracion);
        model.addAttribute("especialidadDelMedico", medicos.stream()
            .filter(m -> m.idMedico().equals(idMedico)).map(MedicoOpcion::idEspecialidad).findFirst().orElse(null));
        return "recepcion/nueva-cita";
    }

    /** Guarda la cita elegida y vuelve a la agenda de ese día. */
    @PostMapping("/cita/nueva")
    public String agendar(@AuthenticationPrincipal UsuarioSesion usuario, @RequestParam Long idPaciente,
            @RequestParam CanalOrigen canal, @RequestParam Short idTipo, @RequestParam Modalidad modalidad,
            @RequestParam Short idEspecialidad, @RequestParam Long idMedico,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam int duracion, @RequestParam(required = false) Long indicacion,
            @RequestParam(required = false) String indicacionesAdmin,
            @RequestParam(required = false) String titular, RedirectAttributes flash) {
        Long idTitular = elegirTitular(pacientes.titulares(idPaciente), titular == null ? "0" : titular);
        try {
            Cita c = reservas.agendar(new NuevaCitaComando(idPaciente, idTitular, idMedico, idEspecialidad, idTipo, inicio,
                duracion, modalidad, canal, usuario.getIdCuenta(), indicacion, null,
                indicacionesAdmin == null || indicacionesAdmin.isBlank() ? null : indicacionesAdmin.trim(), false));
            flash.addFlashAttribute("exito", "Cita agendada para " + c.getPaciente().getNombres() + " "
                + c.getPaciente().getApellidos() + ".");
            return "redirect:/recepcion/agenda?fecha=" + inicio.toLocalDate();
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:" + UriComponentsBuilder.fromPath("/recepcion/cita/nueva")
                .queryParam("idPaciente", idPaciente).queryParam("canal", canal).queryParam("idTipo", idTipo)
                .queryParam("modalidad", modalidad).queryParam("idEspecialidad", idEspecialidad)
                .queryParam("idMedico", idMedico).queryParamIfPresent("indicacion", java.util.Optional.ofNullable(indicacion))
                .queryParam("titular", idTitular == null ? "0" : idTitular)
                .build().toUriString();
        }
    }

    /**
    La cuenta titular elegida, solo si es una de las posibles del paciente. Sin elección, la cuenta propia del
    paciente; «0», sin titular (se llama al teléfono del paciente).
    */
    private static Long elegirTitular(List<OpcionTitular> opciones, String titular) {
        if (titular == null) {
            return opciones.stream().filter(OpcionTitular::propia).map(OpcionTitular::idCuenta).findFirst().orElse(null);
        }
        return opciones.stream().map(OpcionTitular::idCuenta)
            .filter(id -> id.toString().equals(titular.trim())).findFirst().orElse(null);
    }

    /** Citas indicadas por el médico que aún no se agendan, primero las que vencen antes. */
    @GetMapping("/pendientes")
    public String pendientes(Model model) {
        model.addAttribute("pendientes", indicadas.pendientesVista());
        return "recepcion/pendientes";
    }

    /** Resultado de la llamada a un paciente con cita indicada. AGENDO abre la nueva cita con la indicación. */
    @PostMapping("/pendientes/{id}/llamada")
    public String llamadaPendiente(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @RequestParam ResultadoLlamada resultado, @RequestParam(required = false) String observacion,
            RedirectAttributes flash) {
        try {
            indicadas.registrarLlamada(id, resultado, observacion, usuario.getIdCuenta());
            if (resultado == ResultadoLlamada.AGENDO) {
                return "redirect:/recepcion/cita/nueva?indicacion=" + id;
            }
            flash.addFlashAttribute("exito", "Llamada registrada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recepcion/pendientes";
    }
}
