package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.TipoConsulta;

/** Acceso a la tabla tipo_consulta. */
public interface TipoConsultaRepository extends JpaRepository<TipoConsulta, Short> {
}
