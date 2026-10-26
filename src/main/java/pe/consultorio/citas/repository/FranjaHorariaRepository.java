package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.FranjaHoraria;

/** Acceso a la tabla franja_horaria. */
public interface FranjaHorariaRepository extends JpaRepository<FranjaHoraria, Long> {
}
