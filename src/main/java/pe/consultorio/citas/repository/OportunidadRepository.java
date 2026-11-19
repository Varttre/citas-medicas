package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.Oportunidad;

/** Acceso a la tabla oportunidad. */
public interface OportunidadRepository extends JpaRepository<Oportunidad, Long> {

    /** Oportunidades abiertas del médico que se cruzan con {@code [desde, hasta)}, por inicio. */
    @Query("select o from Oportunidad o where o.medico.idMedico = :idMedico and o.estado = 'ABIERTA' "
        + "and o.inicio < :hasta and o.fin > :desde order by o.inicio")
    List<Oportunidad> abiertasQueCruzan(@Param("idMedico") Long idMedico, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Todas las oportunidades abiertas con su médico, las más próximas primero. */
    @Query("select o from Oportunidad o join fetch o.medico where o.estado = 'ABIERTA' order by o.inicio")
    List<Oportunidad> abiertas();

    /** Las abiertas cuya hora ya llegó pasan a EXPIRADA. */
    @Modifying
    @Query("update Oportunidad o set o.estado = 'EXPIRADA', o.cerradaEn = :ahora "
        + "where o.estado = 'ABIERTA' and o.inicio <= :ahora")
    int expirar(@Param("ahora") LocalDateTime ahora);
}
