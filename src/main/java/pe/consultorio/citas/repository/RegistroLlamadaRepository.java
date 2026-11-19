package pe.consultorio.citas.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;

/** Acceso a la tabla registro_llamada. */
public interface RegistroLlamadaRepository extends JpaRepository<RegistroLlamada, Long> {

    /** Si ese teléfono ya se registró alguna vez como número inválido. */
    boolean existsByTelefonoLlamadoAndResultado(String telefonoLlamado, ResultadoLlamada resultado);

    /** Si el paciente ya aceptó un cupo liberado desde {@code desde} (para no ofrecerle otro el mismo día). */
    @Query("select count(r) > 0 from RegistroLlamada r where r.tipoCola = 'CUPO_LIBERADO' and r.resultado = 'ACEPTO' "
        + "and r.cita.paciente.idPaciente = :idPaciente and r.fechaHora >= :desde")
    boolean aceptoCupoDesde(@Param("idPaciente") Long idPaciente, @Param("desde") LocalDateTime desde);

    /** Cuántas llamadas se registraron para la cita en esa cola. */
    long countByCitaIdCitaAndTipoCola(Long idCita, TipoCola tipoCola);

    /** La última llamada registrada para la cita en esa cola. */
    Optional<RegistroLlamada> findFirstByCitaIdCitaAndTipoColaOrderByFechaHoraDesc(Long idCita, TipoCola tipoCola);

    /** Cuántas llamadas se registraron para un horario liberado. */
    long countByOportunidadIdOportunidad(Long idOportunidad);
}
