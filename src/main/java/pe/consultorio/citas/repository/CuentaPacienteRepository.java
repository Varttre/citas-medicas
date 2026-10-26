package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.CuentaPacienteId;

/** Acceso a la tabla cuenta_paciente. */
public interface CuentaPacienteRepository extends JpaRepository<CuentaPaciente, CuentaPacienteId> {
}
