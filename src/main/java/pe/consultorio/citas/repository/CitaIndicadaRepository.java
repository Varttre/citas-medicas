package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CitaIndicada;

/** Acceso a la tabla cita_indicada. */
public interface CitaIndicadaRepository extends JpaRepository<CitaIndicada, Long> {
}
