package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clave primaria compuesta de MedicoTipoConsulta (id_medico, id_tipo_consulta). */
@Embeddable
public class MedicoTipoConsultaId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_medico")
    private Long idMedico;

    @Column(name = "id_tipo_consulta")
    private Short idTipoConsulta;

    public MedicoTipoConsultaId() {
    }

    public MedicoTipoConsultaId(Long idMedico, Short idTipoConsulta) {
        this.idMedico = idMedico;
        this.idTipoConsulta = idTipoConsulta;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public void setIdMedico(Long idMedico) {
        this.idMedico = idMedico;
    }

    public Short getIdTipoConsulta() {
        return idTipoConsulta;
    }

    public void setIdTipoConsulta(Short idTipoConsulta) {
        this.idTipoConsulta = idTipoConsulta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MedicoTipoConsultaId otro)) {
            return false;
        }
        return Objects.equals(idMedico, otro.idMedico)
            && Objects.equals(idTipoConsulta, otro.idTipoConsulta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idMedico, idTipoConsulta);
    }
}
