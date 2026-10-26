package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;

/** Acceso a la tabla cita. */
public interface CitaRepository extends JpaRepository<Cita, Long> {

    /** Estados que ya no ocupan horario. */
    List<EstadoCita> CANCELADAS = List.of(EstadoCita.CANCELADA_POR_PACIENTE, EstadoCita.CANCELADA_POR_CLINICA);

    /** Citas del médico que empiezan desde {@code desde}, sin las de los estados excluidos, con su paciente. */
    @Query("select c from Cita c join fetch c.paciente "
        + "where c.medico.idMedico = :idMedico and c.inicio >= :desde and c.estado not in :excluidos "
        + "order by c.inicio")
    List<Cita> delMedicoDesde(@Param("idMedico") Long idMedico, @Param("desde") LocalDateTime desde,
            @Param("excluidos") Collection<EstadoCita> excluidos);

    /** Citas del médico que se cruzan con {@code [desde, hasta)}, sin las de los estados excluidos, con su paciente. */
    @Query("select c from Cita c join fetch c.paciente "
        + "where c.medico.idMedico = :idMedico and c.inicio < :hasta and c.fin > :desde "
        + "and c.estado not in :excluidos order by c.inicio")
    List<Cita> delMedicoQueCruzan(@Param("idMedico") Long idMedico, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, @Param("excluidos") Collection<EstadoCita> excluidos);
}
