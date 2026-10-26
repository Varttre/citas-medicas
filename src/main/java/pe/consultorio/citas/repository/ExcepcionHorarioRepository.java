package pe.consultorio.citas.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.ExcepcionHorario;

/** Acceso a la tabla excepcion_horario. */
public interface ExcepcionHorarioRepository extends JpaRepository<ExcepcionHorario, Long> {

    /** Excepciones del médico que terminan en {@code desde} o después, por fecha de inicio. */
    List<ExcepcionHorario> findByMedicoIdMedicoAndFechaFinGreaterThanEqualOrderByFechaInicio(Long idMedico,
            LocalDate desde);
}
