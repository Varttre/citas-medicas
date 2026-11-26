package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.CitaEvento;

/** Acceso a la tabla cita_evento. */
public interface CitaEventoRepository extends JpaRepository<CitaEvento, Long> {

    /** Eventos de la cita registrados hasta {@code hasta}, el más reciente primero. */
    List<CitaEvento> findByCitaIdCitaAndFechaHoraLessThanEqualOrderByFechaHoraDesc(Long idCita, LocalDateTime hasta);
}
