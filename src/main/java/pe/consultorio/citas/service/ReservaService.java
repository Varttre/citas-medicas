package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidadId;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.exception.ErroresBaseDatos;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;
import pe.consultorio.citas.service.disponibilidad.BloqueoTemporalService;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Crea citas desde cualquier canal (portal, recepción o consulta del médico). La base de datos impide los solapes con
EXCLUDE USING gist: si dos personas agendan el mismo horario a la vez, una gana y la otra recibe
«Ese horario ya fue ocupado. Elige otro.».
*/
@Service
public class ReservaService {

    private final CitaRepository citas;
    private final CitaEventoRepository eventos;
    private final PacienteRepository pacientes;
    private final MedicoRepository medicos;
    private final TipoConsultaRepository tipos;
    private final EspecialidadRepository especialidades;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final CuentaRepository cuentas;
    private final CuentaPacienteRepository vinculos;
    private final ConfiguracionService configuracion;
    private final PreferenciaService preferencias;
    private final BloqueoTemporalService bloqueos;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public ReservaService(CitaRepository citas, CitaEventoRepository eventos, PacienteRepository pacientes,
            MedicoRepository medicos, TipoConsultaRepository tipos, EspecialidadRepository especialidades,
            MedicoEspecialidadRepository medicoEspecialidades, CuentaRepository cuentas,
            CuentaPacienteRepository vinculos, ConfiguracionService configuracion, PreferenciaService preferencias,
            BloqueoTemporalService bloqueos, AuditoriaService auditoria, Clock reloj) {
        this.citas = citas;
        this.eventos = eventos;
        this.pacientes = pacientes;
        this.medicos = medicos;
        this.tipos = tipos;
        this.especialidades = especialidades;
        this.medicoEspecialidades = medicoEspecialidades;
        this.cuentas = cuentas;
        this.vinculos = vinculos;
        this.configuracion = configuracion;
        this.preferencias = preferencias;
        this.bloqueos = bloqueos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** Valida el comando, guarda la cita en estado PROGRAMADA y registra su evento de creación. */
    @Transactional
    public Cita agendar(NuevaCitaComando c) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Paciente paciente = pacientes.findById(c.idPaciente())
            .orElseThrow(() -> new ReglaNegocioException("El paciente no existe."));
        Medico medico = medicos.findById(c.idMedico())
            .filter(Medico::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("El médico no está disponible."));
        TipoConsulta tipo = tipos.findById(c.idTipoConsulta())
            .filter(TipoConsulta::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("El tipo de consulta no está disponible."));
        if (!medicoEspecialidades.existsById(new MedicoEspecialidadId(c.idMedico(), c.idEspecialidad()))) {
            throw new ReglaNegocioException("El médico no atiende esa especialidad.");
        }

        int incremento = configuracion.incremento();
        if (!Rejilla.enRejilla(c.inicio(), incremento) || c.duracionMin() <= 0 || c.duracionMin() % incremento != 0) {
            throw new ReglaNegocioException("La hora y la duración deben ir en múltiplos de " + incremento + " minutos.");
        }
        if (!c.inicio().isAfter(ahora)) {
            throw new ReglaNegocioException("Elige un horario futuro.");
        }
        if (tipo.getModalidadPermitida() != ModalidadPermitida.AMBAS
                && !tipo.getModalidadPermitida().name().equals(c.modalidad().name())) {
            throw new ReglaNegocioException("Ese tipo de consulta no admite la modalidad elegida.");
        }
        if (c.canal() == CanalOrigen.WEB && !tipo.isReservableWeb() && c.idIndicacion() == null) {
            throw new ReglaNegocioException("Ese tipo de consulta no se puede reservar por la web.");
        }
        Cuenta titular = null;
        if (c.idCuentaTitular() != null) {
            titular = cuentas.findById(c.idCuentaTitular())
                .orElseThrow(() -> new ReglaNegocioException("La cuenta no existe."));
            boolean esPropio = titular.getPacientePropio() != null
                && titular.getPacientePropio().getIdPaciente().equals(paciente.getIdPaciente());
            boolean vinculado = vinculos.delPaciente(paciente.getIdPaciente()).stream()
                .anyMatch(v -> v.getCuenta().getIdCuenta().equals(c.idCuentaTitular()) && v.getRevocadoEn() == null);
            if (!esPropio && !vinculado) {
                throw new ReglaNegocioException("Esta cuenta no puede agendar para ese paciente.");
            }
        }
        if (c.canal() == CanalOrigen.CONSULTA_MEDICA && !c.autorizacionVerbal()) {
            throw new ReglaNegocioException("Confirma que el paciente autorizó la cita de palabra.");
        }

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setCuentaTitular(titular);
        cita.setMedico(medico);
        cita.setEspecialidad(especialidades.getReferenceById(c.idEspecialidad()));
        cita.setTipoConsulta(tipo);
        cita.setInicio(c.inicio());
        cita.setFin(c.inicio().plusMinutes(c.duracionMin()));
        cita.setEstado(EstadoCita.PROGRAMADA);
        cita.setModalidad(c.modalidad());
        cita.setCanalOrigen(c.canal());
        cita.setEsPrimeraVez(citas.delPaciente(paciente.getIdPaciente()).stream()
            .noneMatch(x -> x.getEstado() == EstadoCita.REALIZADA));
        cita.setIndicacionesAdmin(c.indicacionesAdmin());
        cita.setCreadoEn(ahora);
        try {
            cita = citas.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            if (ErroresBaseDatos.esSolape(e)) {
                throw new HorarioOcupadoException();
            }
            throw e;
        }

        CitaEvento ev = new CitaEvento();
        ev.setCita(cita);
        ev.setCuenta(c.idCuentaActor() == null ? null : cuentas.getReferenceById(c.idCuentaActor()));
        ev.setTipoEvento(TipoEvento.CREACION);
        ev.setEstadoNuevo(EstadoCita.PROGRAMADA);
        ev.setInicioNuevo(cita.getInicio());
        ev.setFinNuevo(cita.getFin());
        ev.setFechaHora(ahora);
        eventos.save(ev);

        if (c.preferencia() != null) {
            preferencias.guardar(cita.getIdCita(), c.preferencia(), c.idCuentaActor());
        }
        if (c.canal() == CanalOrigen.CONSULTA_MEDICA) {
            auditoria.registrar(c.idCuentaActor(), AccionAuditoria.AUTORIZACION_VERBAL, "CITA",
                String.valueOf(cita.getIdCita()), null, null);
        }
        if (c.idCuentaActor() != null) {
            bloqueos.liberar(c.idCuentaActor());
        }
        return cita;
    }
}
