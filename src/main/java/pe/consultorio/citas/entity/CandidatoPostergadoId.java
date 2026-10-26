package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clave primaria compuesta de CandidatoPostergado (id_oportunidad, id_cita). */
@Embeddable
public class CandidatoPostergadoId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_oportunidad")
    private Long idOportunidad;

    @Column(name = "id_cita")
    private Long idCita;

    public CandidatoPostergadoId() {
    }

    public CandidatoPostergadoId(Long idOportunidad, Long idCita) {
        this.idOportunidad = idOportunidad;
        this.idCita = idCita;
    }

    public Long getIdOportunidad() {
        return idOportunidad;
    }

    public void setIdOportunidad(Long idOportunidad) {
        this.idOportunidad = idOportunidad;
    }

    public Long getIdCita() {
        return idCita;
    }

    public void setIdCita(Long idCita) {
        this.idCita = idCita;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CandidatoPostergadoId otro)) {
            return false;
        }
        return Objects.equals(idOportunidad, otro.idOportunidad)
            && Objects.equals(idCita, otro.idCita);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idOportunidad, idCita);
    }
}
