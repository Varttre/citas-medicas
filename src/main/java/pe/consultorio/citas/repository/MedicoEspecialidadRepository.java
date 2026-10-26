package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.MedicoEspecialidad;
import pe.consultorio.citas.entity.MedicoEspecialidadId;

/** Acceso a la tabla medico_especialidad. */
public interface MedicoEspecialidadRepository extends JpaRepository<MedicoEspecialidad, MedicoEspecialidadId> {
}
