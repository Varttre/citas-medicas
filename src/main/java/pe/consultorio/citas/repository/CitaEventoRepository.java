package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CitaEvento;

/** Acceso a la tabla cita_evento. */
public interface CitaEventoRepository extends JpaRepository<CitaEvento, Long> {
}
