package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.CitaIndicada;
import pe.consultorio.citas.entity.EstadoIndicacion;

/** Acceso a la tabla cita_indicada. */
public interface CitaIndicadaRepository extends JpaRepository<CitaIndicada, Long> {

    /** Cuántas indicaciones hay en ese estado. */
    long countByEstado(EstadoIndicacion estado);

    /** Indicaciones pendientes que dio el médico (en citas suyas), las que vencen antes primero. */
    @Query("select i from CitaIndicada i join fetch i.citaOrigen c join fetch c.paciente join fetch i.tipoConsulta "
        + "where c.medico.idMedico = :idMedico and i.estado = 'PENDIENTE' order by i.vigenteHasta")
    List<CitaIndicada> pendientesDeMedico(@Param("idMedico") Long idMedico);

    /** Todas las indicaciones pendientes, las que vencen antes primero. */
    @Query("select i from CitaIndicada i join fetch i.citaOrigen c join fetch c.paciente join fetch c.medico "
        + "join fetch i.tipoConsulta where i.estado = 'PENDIENTE' order by i.vigenteHasta")
    List<CitaIndicada> pendientes();
}
