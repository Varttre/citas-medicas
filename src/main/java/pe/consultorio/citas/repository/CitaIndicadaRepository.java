package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.EstadoIndicacion;

/** Acceso a la tabla cita_indicada. */
public interface CitaIndicadaRepository extends JpaRepository<CitaIndicada, Long> {

    /** Cuántas indicaciones hay en ese estado. */
    long countByEstado(EstadoIndicacion estado);
}
