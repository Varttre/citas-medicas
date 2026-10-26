package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Rol;

import java.util.Optional;

/** Acceso a la tabla rol. */
public interface RolRepository extends JpaRepository<Rol, Short> {

    Optional<Rol> findByNombre(NombreRol nombre);
}
