package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Oportunidad;

/** Acceso a la tabla oportunidad. */
public interface OportunidadRepository extends JpaRepository<Oportunidad, Long> {
}
