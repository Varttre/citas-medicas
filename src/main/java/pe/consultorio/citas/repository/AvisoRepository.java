package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Aviso;

/** Acceso a la tabla aviso. */
public interface AvisoRepository extends JpaRepository<Aviso, Long> {
}
