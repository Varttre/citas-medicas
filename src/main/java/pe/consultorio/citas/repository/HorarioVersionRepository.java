package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.HorarioVersion;

/** Acceso a la tabla horario_version. */
public interface HorarioVersionRepository extends JpaRepository<HorarioVersion, Long> {

    /** Versiones del médico, de la más reciente a la más antigua. */
    List<HorarioVersion> findByMedicoIdMedicoOrderByVigenteDesdeDesc(Long idMedico);
}
