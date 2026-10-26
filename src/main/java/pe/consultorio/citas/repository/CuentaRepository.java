package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Cuenta;

import java.util.Optional;

/** Acceso a la tabla cuenta. */
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByCorreo(String correo);
}
