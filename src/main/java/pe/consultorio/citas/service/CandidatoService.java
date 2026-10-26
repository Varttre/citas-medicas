package pe.consultorio.citas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.dto.Candidato;

/** Arma la lista ordenada de pacientes a quienes ofrecer un horario liberado. */
@Service
public class CandidatoService {

    /** Candidatos para el bloque liberado, ordenados por puntaje; los postergados van al final. */
    public List<Candidato> candidatos(Long idOportunidad) {
        throw new UnsupportedOperationException("Pendiente: A9");
    }

    /** Recepción deja a un candidato para el final de la lista de ese bloque. */
    public void postergar(Long idOportunidad, Long idCita, Long actor) {
        throw new UnsupportedOperationException("Pendiente: A9");
    }
}
