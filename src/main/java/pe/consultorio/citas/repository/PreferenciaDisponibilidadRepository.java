package pe.consultorio.citas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;

/** Acceso a la tabla preferencia_disponibilidad. */
public interface PreferenciaDisponibilidadRepository extends JpaRepository<PreferenciaDisponibilidad, Long> {

    Optional<PreferenciaDisponibilidad> findByCitaIdCita(Long idCita);

    /** Preferencias sin revocar de citas aún vigentes, con la cita, su médico, paciente y especialidad. */
    @Query("select p from PreferenciaDisponibilidad p join fetch p.cita c join fetch c.medico "
        + "join fetch c.paciente join fetch c.especialidad "
        + "where p.revocadoEn is null and c.estado in ('PROGRAMADA', 'CONFIRMADA')")
    List<PreferenciaDisponibilidad> vigentes();
}
