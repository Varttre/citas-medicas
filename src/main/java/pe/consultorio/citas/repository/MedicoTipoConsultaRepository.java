package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.MedicoTipoConsultaId;

/** Acceso a la tabla medico_tipo_consulta. */
public interface MedicoTipoConsultaRepository extends JpaRepository<MedicoTipoConsulta, MedicoTipoConsultaId> {

    /** Duraciones del médico con su tipo de consulta ya cargado, por nombre del tipo. */
    @Query("select mt from MedicoTipoConsulta mt join fetch mt.tipoConsulta t "
        + "where mt.medico.idMedico = :idMedico order by t.nombre")
    List<MedicoTipoConsulta> delMedico(@Param("idMedico") Long idMedico);
}
