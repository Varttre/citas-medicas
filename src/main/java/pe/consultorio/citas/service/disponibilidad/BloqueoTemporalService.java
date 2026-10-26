package pe.consultorio.citas.service.disponibilidad;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.entity.BloqueoTemporal;

/** Retiene un horario unos minutos mientras la persona termina de llenar la reserva. */
@Service
public class BloqueoTemporalService {

    /** Retiene {@code [inicio, fin)} del médico para la cuenta; sustituye la retención anterior de esa cuenta. */
    public BloqueoTemporal tomar(Long idCuenta, Long idMedico, LocalDateTime inicio, LocalDateTime fin) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }

    /** La retención aún vigente de la cuenta, si tiene una. */
    public Optional<BloqueoTemporal> vigenteDe(Long idCuenta) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }

    /** Segundos que le quedan a la retención (0 si ya venció). */
    public long segundosRestantes(BloqueoTemporal b) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }

    /** Suelta la retención de la cuenta, si tiene una. */
    public void liberar(Long idCuenta) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }
}
