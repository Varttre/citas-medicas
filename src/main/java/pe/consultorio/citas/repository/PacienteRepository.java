package pe.consultorio.citas.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoDocumento;

/** Acceso a la tabla paciente. */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByTipoDocumentoAndNroDocumento(TipoDocumento tipoDocumento, String nroDocumento);

    /** Búsqueda de recepción por documento, nombres, apellidos o teléfono (sin distinguir mayúsculas). */
    @Query("select p from Paciente p where lower(p.nroDocumento) like :patron or lower(p.nombres) like :patron "
        + "or lower(p.apellidos) like :patron or lower(concat(p.nombres, ' ', p.apellidos)) like :patron "
        + "or p.telefono like :patron order by p.apellidos, p.nombres")
    List<Paciente> buscar(@Param("patron") String patron, Pageable pagina);
}
