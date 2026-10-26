package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Especialidad;

/** Acceso a la tabla especialidad. */
public interface EspecialidadRepository extends JpaRepository<Especialidad, Short> {
}
