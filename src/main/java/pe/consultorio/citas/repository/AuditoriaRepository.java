package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.consultorio.citas.entity.Auditoria;

/** Acceso a la tabla auditoria. */
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /** Registros de esa acción hechos por la cuenta desde {@code desde}, los más recientes primero. */
    List<Auditoria> findByCuentaIdCuentaAndAccionAndFechaHoraGreaterThanEqualOrderByFechaHoraDesc(Long idCuenta,
            String accion, LocalDateTime desde);
}
