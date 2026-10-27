package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.BloqueoTemporal;

/** Acceso a la tabla bloqueo_temporal. */
public interface BloqueoTemporalRepository extends JpaRepository<BloqueoTemporal, Long> {

    /** Retenciones vigentes del médico que se cruzan con {@code [desde, hasta)}. */
    @Query("select b from BloqueoTemporal b where b.medico.idMedico = :idMedico and b.expiraEn > :ahora "
        + "and b.inicio < :hasta and b.fin > :desde")
    List<BloqueoTemporal> vigentesQueCruzan(@Param("idMedico") Long idMedico, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, @Param("ahora") LocalDateTime ahora);

    Optional<BloqueoTemporal> findFirstByCuentaIdCuentaAndExpiraEnAfter(Long idCuenta, LocalDateTime ahora);

    @Modifying
    @Query("delete from BloqueoTemporal b where b.expiraEn <= :ahora or b.cuenta.idCuenta = :idCuenta")
    int borrarVencidosYDeCuenta(@Param("ahora") LocalDateTime ahora, @Param("idCuenta") Long idCuenta);

    @Modifying
    @Query("delete from BloqueoTemporal b where b.cuenta.idCuenta = :idCuenta")
    int borrarDeCuenta(@Param("idCuenta") Long idCuenta);
}
