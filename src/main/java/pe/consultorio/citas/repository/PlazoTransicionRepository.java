package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.PlazoTransicion;
import pe.consultorio.citas.entity.Transicion;

/** Acceso a la tabla plazo_transicion. */
public interface PlazoTransicionRepository extends JpaRepository<PlazoTransicion, Transicion> {
}
