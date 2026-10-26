package pe.consultorio.citas.service;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.Cita;

/** Crea citas desde cualquier canal. Si el horario ya está ocupado lanza HorarioOcupadoException. */
@Service
public class ReservaService {

    /** Valida el comando, guarda la cita en estado PROGRAMADA y registra su evento de creación. */
    public Cita agendar(NuevaCitaComando c) {
        throw new UnsupportedOperationException("Pendiente: A5");
    }
}
