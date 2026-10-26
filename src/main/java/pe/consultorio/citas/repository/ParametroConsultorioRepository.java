package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.ParametroConsultorio;

/** Acceso a la tabla parametro_consultorio. */
public interface ParametroConsultorioRepository extends JpaRepository<ParametroConsultorio, Short> {
}
