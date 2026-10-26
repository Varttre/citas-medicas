package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Paciente;

/** Acceso a la tabla paciente. */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {
}
