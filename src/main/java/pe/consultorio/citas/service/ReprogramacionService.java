package pe.consultorio.citas.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

/** Mueve una cita a otro horario conservando su identificador. */
@Service
public class ReprogramacionService {

    /** Cambia el horario de la cita; vuelve a PROGRAMADA y libera el horario anterior. */
    public void reprogramar(Long idCita, LocalDateTime nuevoInicio, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A7");
    }

    /** Reprograma la cita a un horario liberado que el paciente aceptó y da por usada su preferencia. */
    public void adelantarPorCupo(Long idCita, Long idOportunidad, LocalDateTime nuevoInicio, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A7");
    }
}
