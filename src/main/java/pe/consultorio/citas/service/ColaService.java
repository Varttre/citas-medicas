package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.FilaCola;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;

/**
Fuentes de datos de las colas de llamadas de recepción. Las tres colas usan la misma tabla en pantalla
({@code fragmentos/cola.html}); aquí solo cambia de dónde salen las filas.
*/
@Service
@Transactional(readOnly = true)
public class ColaService {

    /** Texto de cada resultado de llamada, para la columna «Estado» y los botones del modal. */
    public static final Map<ResultadoLlamada, String> TEXTO = Map.ofEntries(
        Map.entry(ResultadoLlamada.CONFIRMO, "Confirmó"),
        Map.entry(ResultadoLlamada.NO_CONTESTO, "No contestó"),
        Map.entry(ResultadoLlamada.CANCELO, "Canceló"),
        Map.entry(ResultadoLlamada.REPROGRAMO, "Reprogramó"),
        Map.entry(ResultadoLlamada.NUMERO_INVALIDO, "Número inválido"),
        Map.entry(ResultadoLlamada.ACEPTO, "Aceptó"),
        Map.entry(ResultadoLlamada.RECHAZO, "Rechazó"),
        Map.entry(ResultadoLlamada.NO_PUEDE_LLEGAR, "No puede llegar"),
        Map.entry(ResultadoLlamada.OTRO, "Otro"),
        Map.entry(ResultadoLlamada.AGENDO, "Agendó"),
        Map.entry(ResultadoLlamada.NO_LE_INTERESA, "No le interesa"));
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CitaRepository citas;
    private final RegistroLlamadaRepository llamadas;
    private final OportunidadService oportunidades;
    private final CandidatoService candidatos;
    private final ContactoService contacto;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public ColaService(CitaRepository citas, RegistroLlamadaRepository llamadas, OportunidadService oportunidades,
            CandidatoService candidatos, ContactoService contacto, ConfiguracionService configuracion, Clock reloj) {
        this.citas = citas;
        this.llamadas = llamadas;
        this.oportunidades = oportunidades;
        this.candidatos = candidatos;
        this.contacto = contacto;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /**
    Citas por confirmar: programadas que empiezan dentro de las próximas {@code anticipacion_confirm_h} horas, las más
    próximas primero. Una cita cancelada desde el portal desaparece sola porque ya no está programada. Filtros
    opcionales por día, médico y especialidad.
    */
    public List<FilaCola> confirmaciones(LocalDate fecha, Long idMedico, Short idEspecialidad) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDateTime hasta = ahora.plusHours(configuracion.parametros().getAnticipacionConfirmH());
        return citas.programadasEntre(ahora, hasta).stream()
            .filter(c -> fecha == null || c.getInicio().toLocalDate().equals(fecha))
            .filter(c -> idMedico == null || c.getMedico().getIdMedico().equals(idMedico))
            .filter(c -> idEspecialidad == null || c.getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .map(this::filaConfirmacion)
            .toList();
    }

    /** Horarios liberados abiertos, los más próximos primero, con cuántos candidatos tiene cada uno. */
    public List<FilaCola> cupos() {
        return oportunidades.abiertas().stream().map(this::filaCupo).toList();
    }

    private FilaCola filaConfirmacion(Cita c) {
        String telefono = contacto.telefonoDe(c);
        String estado = llamadas.findFirstByCitaIdCitaAndTipoColaOrderByFechaHoraDesc(c.getIdCita(),
            TipoCola.CONFIRMACION).map(l -> TEXTO.get(l.getResultado())).orElse("Sin llamar");
        String titular = c.getCuentaTitular() != null && c.getCuentaTitular().getPacientePropio() != null
            && !c.getCuentaTitular().getPacientePropio().getIdPaciente().equals(c.getPaciente().getIdPaciente())
            ? "Contacto: " + nombre(c.getCuentaTitular().getPacientePropio().getNombres(),
                c.getCuentaTitular().getPacientePropio().getApellidos()) + " (titular)" : null;
        return new FilaCola(c.getIdCita(), nombre(c.getPaciente().getNombres(), c.getPaciente().getApellidos()),
            telefono, contacto.telefonoInvalido(telefono), nombre(c.getMedico().getNombres(), c.getMedico().getApellidos()),
            c.getInicio(), titular, llamadas.countByCitaIdCitaAndTipoCola(c.getIdCita(), TipoCola.CONFIRMACION), estado,
            false);
    }

    private FilaCola filaCupo(Oportunidad o) {
        int n = candidatos.candidatos(o.getIdOportunidad()).size();
        String detalle = o.getInicio().format(HORA) + "–" + o.getFin().format(HORA) + " · "
            + (n == 1 ? "1 candidato" : n + " candidatos");
        return new FilaCola(o.getIdOportunidad(), null, null, false,
            nombre(o.getMedico().getNombres(), o.getMedico().getApellidos()), o.getInicio(), detalle,
            llamadas.countByOportunidadIdOportunidad(o.getIdOportunidad()), "Abierto", false);
    }

    private static String nombre(String nombres, String apellidos) {
        return nombres + " " + apellidos;
    }
}
