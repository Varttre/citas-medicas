package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.IndicacionComando;
import pe.consultorio.citas.dto.IndicacionVista;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoIndicacion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/**
Citas que el médico indica para más adelante. La indicación es del paciente de la cita de origen y puede atenderla
cualquier médico que ofrezca ese tipo de consulta.
*/
@Service
@Transactional(readOnly = true)
public class CitaIndicadaService {

    private final CitaIndicadaRepository indicadas;
    private final CitaRepository citas;
    private final TipoConsultaRepository tipos;
    private final MedicoTipoConsultaRepository duraciones;
    private final ConfiguracionService configuracion;
    private final RegistroLlamadaRepository llamadas;
    private final CuentaRepository cuentas;
    private final ContactoService contacto;
    private final CuentaPacienteRepository vinculos;
    private final Clock reloj;

    public CitaIndicadaService(CitaIndicadaRepository indicadas, CitaRepository citas, TipoConsultaRepository tipos,
            MedicoTipoConsultaRepository duraciones, ConfiguracionService configuracion,
            RegistroLlamadaRepository llamadas, CuentaRepository cuentas, ContactoService contacto,
            CuentaPacienteRepository vinculos, Clock reloj) {
        this.vinculos = vinculos;
        this.llamadas = llamadas;
        this.cuentas = cuentas;
        this.contacto = contacto;
        this.indicadas = indicadas;
        this.citas = citas;
        this.tipos = tipos;
        this.duraciones = duraciones;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /** El médico de la cita de origen indica una siguiente cita; queda PENDIENTE. */
    @Transactional
    public CitaIndicada indicar(Long idCitaOrigen, IndicacionComando c, Long actor) {
        Cita origen = citas.findById(idCitaOrigen)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCitaOrigen));
        if (origen.getEstado() != EstadoCita.ASISTIO && origen.getEstado() != EstadoCita.REALIZADA) {
            throw new ReglaNegocioException("Solo se indica una cita desde una consulta atendida.");
        }
        TipoConsulta tipo = tipos.findById(c.idTipoConsulta())
            .filter(TipoConsulta::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("El tipo de consulta no está disponible."));
        int incremento = configuracion.incremento();
        if (c.duracionMin() <= 0 || c.duracionMin() % incremento != 0) {
            throw new ReglaNegocioException("La duración debe ir en múltiplos de " + incremento + " minutos.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate recomendada = c.fechaRecomendada() != null ? c.fechaRecomendada() : hoy;
        if (recomendada.isBefore(hoy)) {
            throw new ReglaNegocioException("La fecha recomendada no puede ser anterior a hoy.");
        }
        LocalDate vigente = c.vigenteHasta() != null ? c.vigenteHasta()
            : recomendada.plusDays(configuracion.parametros().getVigenciaIndicadaDias());
        if (vigente.isBefore(recomendada)) {
            throw new ReglaNegocioException("La vigencia debe terminar después de la fecha recomendada.");
        }
        CitaIndicada i = new CitaIndicada();
        i.setCitaOrigen(origen);
        i.setTipoConsulta(tipo);
        i.setDuracion((short) c.duracionMin());
        i.setFechaRecomendada(recomendada);
        i.setVigenteHasta(vigente);
        i.setIndicaciones(c.indicaciones() == null || c.indicaciones().isBlank() ? null : c.indicaciones().trim());
        i.setEstado(EstadoIndicacion.PENDIENTE);
        i.setCreadoEn(LocalDateTime.now(reloj));
        return indicadas.save(i);
    }

    /** Descarta una indicación pendiente; el motivo es obligatorio. */
    @Transactional
    public void descartar(Long idIndicacion, String motivo, Long actor) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("Indica el motivo del descarte.");
        }
        CitaIndicada i = pendiente(idIndicacion);
        i.setEstado(EstadoIndicacion.DESCARTADA);
        i.setMotivoDescarte(motivo.trim().length() > 255 ? motivo.trim().substring(0, 255) : motivo.trim());
    }

    /**
    Llamada de la cola de citas pendientes por agendar. NO_LE_INTERESA descarta la indicación (la observación es el
    motivo, obligatorio); AGENDO solo registra (la pantalla abre la nueva cita con los datos de la indicación);
    NO_CONTESTO y NUMERO_INVALIDO solo registran.
    */
    @Transactional
    public RegistroLlamada registrarLlamada(Long idIndicacion, ResultadoLlamada resultado, String observacion,
            Long actor) {
        if (resultado != ResultadoLlamada.AGENDO && resultado != ResultadoLlamada.NO_CONTESTO
                && resultado != ResultadoLlamada.NO_LE_INTERESA && resultado != ResultadoLlamada.NUMERO_INVALIDO) {
            throw new ReglaNegocioException("Ese resultado no corresponde a la cola de citas pendientes.");
        }
        boolean sinTexto = observacion == null || observacion.isBlank();
        if (resultado == ResultadoLlamada.NO_LE_INTERESA && sinTexto) {
            throw new ReglaNegocioException("Indica el motivo por el que no le interesa.");
        }
        CitaIndicada i = pendiente(idIndicacion);
        String telefono = contacto.telefonoDe(i.getCitaOrigen());
        if (telefono == null || telefono.isBlank()) {
            throw new ReglaNegocioException("El paciente no tiene un teléfono de contacto registrado.");
        }
        RegistroLlamada l = new RegistroLlamada();
        l.setCuentaOperador(cuentas.getReferenceById(actor));
        l.setTipoCola(TipoCola.CITA_PENDIENTE);
        l.setIndicacion(i);
        l.setTelefonoLlamado(telefono);
        l.setResultado(resultado);
        l.setObservacion(sinTexto ? null : observacion.trim().substring(0, Math.min(255, observacion.trim().length())));
        l.setFechaHora(LocalDateTime.now(reloj));
        l = llamadas.save(l);
        if (resultado == ResultadoLlamada.NO_LE_INTERESA) {
            descartar(idIndicacion, observacion, actor);
        }
        return l;
    }

    /** La indicación pasa a AGENDADA con la cita que se generó para ella. */
    @Transactional
    public void marcarAgendada(Long idIndicacion, Cita generada) {
        CitaIndicada i = pendiente(idIndicacion);
        if (!i.getCitaOrigen().getPaciente().getIdPaciente().equals(generada.getPaciente().getIdPaciente())) {
            throw new ReglaNegocioException("La cita indicada es solo para el paciente que la recibió.");
        }
        i.setEstado(EstadoIndicacion.AGENDADA);
        i.setCitaGenerada(generada);
    }

    /** Una indicación cualquiera, para mostrarla. */
    public CitaIndicada buscar(Long idIndicacion) {
        return indicadas.findById(idIndicacion)
            .orElseThrow(() -> new RecursoNoEncontradoException("Indicación inexistente " + idIndicacion));
    }

    /** Las pendientes y vigentes, como filas para la cola de recepción. */
    public List<IndicacionVista> pendientesVista() {
        return pendientes().stream().map(this::vista).toList();
    }

    /** Una indicación como fila para mostrarla. */
    public IndicacionVista vista(Long idIndicacion) {
        return vista(buscar(idIndicacion));
    }

    private IndicacionVista vista(CitaIndicada i) {
        Cita o = i.getCitaOrigen();
        return new IndicacionVista(i.getIdIndicacion(), o.getPaciente().getIdPaciente(),
            o.getPaciente().getNombres() + " " + o.getPaciente().getApellidos(), contacto.telefonoDe(o),
            o.getMedico().getNombres() + " " + o.getMedico().getApellidos(),
            i.getTipoConsulta().getIdTipoConsulta(), i.getTipoConsulta().getNombre(), i.getDuracion(),
            i.getFechaRecomendada(), i.getVigenteHasta(),
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(reloj), i.getVigenteHasta()),
            i.getIndicaciones(), i.getEstado().name());
    }

    /** Todas las indicaciones pendientes y vigentes, primero las que vencen antes. */
    public List<CitaIndicada> pendientes() {
        LocalDate hoy = LocalDate.now(reloj);
        return indicadas.pendientes().stream().filter(i -> !i.getVigenteHasta().isBefore(hoy)).toList();
    }

    /** Indicaciones pendientes y vigentes de un paciente. */
    public List<CitaIndicada> pendientesDePaciente(Long idPaciente) {
        return pendientes().stream()
            .filter(i -> i.getCitaOrigen().getPaciente().getIdPaciente().equals(idPaciente))
            .toList();
    }

    /** Las pendientes y vigentes de un paciente, como filas (ficha de recepción y nueva cita). */
    public List<IndicacionVista> pendientesVistaDePaciente(Long idPaciente) {
        return pendientesDePaciente(idPaciente).stream().map(this::vista).toList();
    }

    /**
    Las pendientes y vigentes que ve una cuenta del portal: las de su paciente propio y las de los terceros que puede
    agendar (vínculo sin revocar). La cita indicada es del paciente que la recibió, así que se muestra con su nombre.
    */
    public List<IndicacionVista> pendientesDeCuenta(Long idCuenta) {
        Set<Long> suyos = pacientesDeCuenta(idCuenta);
        return pendientes().stream()
            .filter(i -> suyos.contains(i.getCitaOrigen().getPaciente().getIdPaciente()))
            .map(this::vista)
            .toList();
    }

    /** La indicación, si está pendiente, vigente y es de un paciente que la cuenta puede agendar; si no, error. */
    public IndicacionVista pendienteDeCuenta(Long idCuenta, Long idIndicacion) {
        return pendientesDeCuenta(idCuenta).stream()
            .filter(v -> v.idIndicacion().equals(idIndicacion)).findFirst()
            .orElseThrow(() -> new ReglaNegocioException("Esa cita indicada ya no está pendiente."));
    }

    /** Si el médico atiende el tipo de consulta de la indicación. */
    public boolean medicoAtiende(Long idMedico, Short idTipoConsulta) {
        return duraciones.delMedico(idMedico).stream()
            .anyMatch(mt -> mt.getTipoConsulta().getIdTipoConsulta().equals(idTipoConsulta));
    }

    /**
    Para la agenda del médico: por cada cita, las indicaciones pendientes y vigentes de su paciente (las haya dado él
    u otro médico), salvo la que nació en esa misma cita, que la agenda ya muestra aparte.
    */
    public Map<Long, List<IndicacionVista>> pendientesPorCita(Collection<Long> idCitas) {
        Map<Long, List<IndicacionVista>> porCita = new LinkedHashMap<>();
        if (idCitas.isEmpty()) {
            return porCita;
        }
        Map<Long, List<CitaIndicada>> porPaciente = pendientes().stream()
            .collect(Collectors.groupingBy(i -> i.getCitaOrigen().getPaciente().getIdPaciente()));
        for (Cita c : citas.findAllById(idCitas)) {
            List<IndicacionVista> suyas = porPaciente.getOrDefault(c.getPaciente().getIdPaciente(), List.of()).stream()
                .filter(i -> !i.getCitaOrigen().getIdCita().equals(c.getIdCita()))
                .map(this::vista)
                .toList();
            if (!suyas.isEmpty()) {
                porCita.put(c.getIdCita(), suyas);
            }
        }
        return porCita;
    }

    private Set<Long> pacientesDeCuenta(Long idCuenta) {
        Set<Long> ids = new java.util.HashSet<>();
        cuentas.findById(idCuenta).map(c -> c.getPacientePropio()).ifPresent(p -> ids.add(p.getIdPaciente()));
        vinculos.vigentesDeCuenta(idCuenta).forEach(v -> ids.add(v.getPaciente().getIdPaciente()));
        return ids;
    }

    /** Indicaciones pendientes y vigentes que dio un médico. */
    public List<CitaIndicada> pendientesDeMedico(Long idMedico) {
        LocalDate hoy = LocalDate.now(reloj);
        return indicadas.pendientesDeMedico(idMedico).stream().filter(i -> !i.getVigenteHasta().isBefore(hoy)).toList();
    }

    /**
    La indicación pendiente y vigente del paciente cuyo tipo atiende ese médico, la haya indicado él o no. Si hay
    varias, la que vence antes.
    */
    public Optional<CitaIndicada> pendienteDePacienteParaMedico(Long idPaciente, Long idMedico) {
        return pendientesDePaciente(idPaciente).stream()
            .filter(i -> duraciones.delMedico(idMedico).stream()
                .anyMatch(mt -> mt.getTipoConsulta().getIdTipoConsulta().equals(i.getTipoConsulta().getIdTipoConsulta())))
            .min(Comparator.comparing(CitaIndicada::getVigenteHasta));
    }

    /** Médicos activos que tienen duración configurada para ese tipo de consulta. */
    public List<Medico> medicosQueAtienden(Short idTipoConsulta) {
        return duraciones.findAll().stream()
            .filter(mt -> mt.getTipoConsulta().getIdTipoConsulta().equals(idTipoConsulta) && mt.getMedico().isActivo())
            .map(mt -> mt.getMedico())
            .distinct()
            .toList();
    }

    /** Pasa a VENCIDA las pendientes cuya vigencia ya terminó; devuelve cuántas cambió. */
    @Transactional
    public int vencerVencidas() {
        LocalDate hoy = LocalDate.now(reloj);
        int n = 0;
        for (CitaIndicada i : indicadas.pendientes()) {
            if (i.getVigenteHasta().isBefore(hoy)) {
                i.setEstado(EstadoIndicacion.VENCIDA);
                n++;
            }
        }
        return n;
    }

    private CitaIndicada pendiente(Long idIndicacion) {
        CitaIndicada i = buscar(idIndicacion);
        if (i.getEstado() != EstadoIndicacion.PENDIENTE) {
            throw new ReglaNegocioException("Esa indicación ya no está pendiente.");
        }
        return i;
    }
}
