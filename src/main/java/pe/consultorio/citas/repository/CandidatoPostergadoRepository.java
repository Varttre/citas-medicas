package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CandidatoPostergado;
import pe.consultorio.citas.entity.CandidatoPostergadoId;

/** Acceso a la tabla candidato_postergado. */
public interface CandidatoPostergadoRepository extends JpaRepository<CandidatoPostergado, CandidatoPostergadoId> {

    /** Candidatos postergados de una oportunidad. */
    List<CandidatoPostergado> findByIdIdOportunidad(Long idOportunidad);
}
