package pe.consultorio.citas.service;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.VentanaPlazo;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Transicion;

/**
Cambios de estado de una cita. Cada método comprueba el estado actual, el plazo y el permiso de quien actúa, y deja
un evento en el historial de la cita.
*/
@Service
public class CitaEstadoService {

    /** PROGRAMADA a CONFIRMADA. Solo desde la cola de confirmaciones, con la llamada ya registrada. */
    public void confirmar(Long idCita, Long idLlamada, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Cancelación pedida por el paciente; libera el horario. */
    public void cancelarPorPaciente(Long idCita, Long actor, String motivo) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Cancelación decidida por el consultorio; el motivo es obligatorio. Libera el horario. */
    public void cancelarPorClinica(Long idCita, Long actor, String motivo) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Recepción registra que el paciente llegó (pasa a ASISTIO). */
    public void marcarLlegada(Long idCita, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** El médico registra el inicio de la atención. Exige que la llegada ya esté registrada. */
    public void iniciarAtencion(Long idCita, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** El médico registra el fin de la atención (pasa a REALIZADA). */
    public void finalizarAtencion(Long idCita, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** El paciente no se presentó. */
    public void marcarNoAsistio(Long idCita, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** El paciente llegó y se fue sin ser atendido; el motivo es obligatorio. */
    public void marcarRetiro(Long idCita, Long actor, String motivo) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Cambia la duración de una cita aún no atendida; si se acorta, libera el sobrante. */
    public void ajustarDuracion(Long idCita, int nuevaDuracionMin, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Indicaciones del médico para el paciente. */
    public void guardarIndicaciones(Long idCita, String texto, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Notas administrativas de recepción. */
    public void guardarIndicacionesAdmin(Long idCita, String texto, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }

    /** Ventana de tiempo en la que se permite la transición {@code t} sobre la cita. */
    public VentanaPlazo ventana(Cita cita, Transicion t) {
        throw new UnsupportedOperationException("Pendiente: A6");
    }
}
