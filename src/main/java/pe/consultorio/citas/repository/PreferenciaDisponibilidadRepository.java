package pe.consultorio.citas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;

/** Acceso a la tabla preferencia_disponibilidad. */
public interface PreferenciaDisponibilidadRepository extends JpaRepository<PreferenciaDisponibilidad, Long> {
}
