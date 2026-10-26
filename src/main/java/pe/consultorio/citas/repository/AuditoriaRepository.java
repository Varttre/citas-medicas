package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Auditoria;

/** Acceso a la tabla auditoria. */
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
}
