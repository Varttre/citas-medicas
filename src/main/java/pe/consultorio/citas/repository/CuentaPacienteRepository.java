package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.CuentaPacienteId;

public interface CuentaPacienteRepository extends JpaRepository<CuentaPaciente, CuentaPacienteId> {

    /** Cuentas vinculadas a un paciente, con su cuenta cargada. */
    @Query("select cp from CuentaPaciente cp join fetch cp.cuenta where cp.paciente.idPaciente = :idPaciente "
        + "order by cp.consentimientoEn")
    List<CuentaPaciente> delPaciente(@Param("idPaciente") Long idPaciente);
}
