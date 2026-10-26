package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Medico;

import java.util.Optional;

/** Acceso a la tabla medico. */
public interface MedicoRepository extends JpaRepository<Medico, Long> {

    Optional<Medico> findByCuentaIdCuenta(Long idCuenta);
}
