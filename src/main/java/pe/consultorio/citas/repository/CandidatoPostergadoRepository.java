package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CandidatoPostergado;
import pe.consultorio.citas.entity.CandidatoPostergadoId;

/** Acceso a la tabla candidato_postergado. */
public interface CandidatoPostergadoRepository extends JpaRepository<CandidatoPostergado, CandidatoPostergadoId> {
}
