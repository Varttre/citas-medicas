package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Especialidades de cada médico. Tabla medico_especialidad. */
@Entity
@Table(name = "medico_especialidad")
public class MedicoEspecialidad {

    @EmbeddedId
    private MedicoEspecialidadId id = new MedicoEspecialidadId();

    /** Médico. */
    @MapsId("idMedico")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /** Especialidad que ejerce. Un médico puede tener varias. */
    @MapsId("idEspecialidad")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_especialidad")
    private Especialidad especialidad;

    public MedicoEspecialidadId getId() {
        return id;
    }

    public void setId(MedicoEspecialidadId id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(Especialidad especialidad) {
        this.especialidad = especialidad;
    }
}
