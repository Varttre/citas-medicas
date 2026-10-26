package pe.consultorio.citas.service;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.PreferenciaDatos;

/** Guarda o retira el permiso del paciente para que lo llamen si se libera un horario antes de su cita. */
@Service
public class PreferenciaService {

    /** Crea la preferencia de la cita o la actualiza sin cambiar su antigüedad en la lista. */
    public void guardar(Long idCita, PreferenciaDatos d, Long idCuentaActor) {
        throw new UnsupportedOperationException("Pendiente: A5");
    }

    /** Retira la preferencia de la cita. */
    public void revocar(Long idCita, Long idCuentaActor) {
        throw new UnsupportedOperationException("Pendiente: A5");
    }
}
