package pe.consultorio.citas.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.IndicacionComando;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.Medico;

/**
Citas que el médico indica para más adelante. La indicación es del paciente de la cita de origen y puede atenderla
cualquier médico que ofrezca ese tipo de consulta.
*/
@Service
public class CitaIndicadaService {

    /** El médico de la cita de origen indica una siguiente cita; queda PENDIENTE. */
    public CitaIndicada indicar(Long idCitaOrigen, IndicacionComando c, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Descarta una indicación pendiente; el motivo es obligatorio. */
    public void descartar(Long idIndicacion, String motivo, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Todas las indicaciones pendientes, primero las que vencen antes. */
    public List<CitaIndicada> pendientes() {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Indicaciones pendientes y vigentes de un paciente. */
    public List<CitaIndicada> pendientesDePaciente(Long idPaciente) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Indicaciones pendientes y vigentes que dio un médico. */
    public List<CitaIndicada> pendientesDeMedico(Long idMedico) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /**
    La indicación pendiente y vigente del paciente cuyo tipo atiende ese médico, la haya indicado él o no. Si hay
    varias, la que vence antes.
    */
    public Optional<CitaIndicada> pendienteDePacienteParaMedico(Long idPaciente, Long idMedico) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Médicos activos que tienen duración configurada para ese tipo de consulta. */
    public List<Medico> medicosQueAtienden(Short idTipoConsulta) {
        throw new UnsupportedOperationException("Pendiente: A10");
    }

    /** Pasa a VENCIDA las pendientes cuya vigencia ya terminó; devuelve cuántas cambió. */
    public int vencerVencidas() {
        throw new UnsupportedOperationException("Pendiente: A10");
    }
}
