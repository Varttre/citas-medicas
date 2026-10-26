package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clave primaria compuesta de MedicoEspecialidad (id_medico, id_especialidad). */
@Embeddable
public class MedicoEspecialidadId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_medico")
    private Long idMedico;

    @Column(name = "id_especialidad")
    private Short idEspecialidad;

    public MedicoEspecialidadId() {
    }

    public MedicoEspecialidadId(Long idMedico, Short idEspecialidad) {
        this.idMedico = idMedico;
        this.idEspecialidad = idEspecialidad;
    }

    public Long getIdMedico() {
        return idMedico;
    }

    public void setIdMedico(Long idMedico) {
        this.idMedico = idMedico;
    }

    public Short getIdEspecialidad() {
        return idEspecialidad;
    }

    public void setIdEspecialidad(Short idEspecialidad) {
        this.idEspecialidad = idEspecialidad;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MedicoEspecialidadId otro)) {
            return false;
        }
        return Objects.equals(idMedico, otro.idMedico)
            && Objects.equals(idEspecialidad, otro.idEspecialidad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idMedico, idEspecialidad);
    }
}
