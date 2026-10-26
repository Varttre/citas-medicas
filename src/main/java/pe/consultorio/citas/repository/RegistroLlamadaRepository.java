package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.RegistroLlamada;

/** Acceso a la tabla registro_llamada. */
public interface RegistroLlamadaRepository extends JpaRepository<RegistroLlamada, Long> {
}
