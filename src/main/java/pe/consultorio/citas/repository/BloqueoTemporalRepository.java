package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.BloqueoTemporal;

/** Acceso a la tabla bloqueo_temporal. */
public interface BloqueoTemporalRepository extends JpaRepository<BloqueoTemporal, Long> {
}
