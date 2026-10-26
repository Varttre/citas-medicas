package pe.consultorio.citas.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Oportunidad;

/** Registra los horarios liberados como bloques completos y los cierra cuando alguien los ocupa. */
@Service
public class OportunidadService {

    /** Registra el bloque libre que contiene {@code [inicio, fin)} tras cancelar, reprogramar o acortar la cita. */
    public void liberar(Cita cita, LocalDateTime inicio, LocalDateTime fin) {
        throw new UnsupportedOperationException("Pendiente: A8");
    }

    /** Marca como cubiertos los bloques abiertos que ocupa la cita nueva y vuelve a ofrecer lo que sobra. */
    public void registrarCobertura(Cita nueva) {
        throw new UnsupportedOperationException("Pendiente: A8");
    }

    /** Bloques abiertos, los más próximos primero. */
    public List<Oportunidad> abiertas() {
        throw new UnsupportedOperationException("Pendiente: A8");
    }
}
