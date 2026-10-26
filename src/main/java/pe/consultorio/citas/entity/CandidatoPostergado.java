package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Tabla candidato_postergado. Tabla candidato_postergado. */
@Entity
@Table(name = "candidato_postergado")
public class CandidatoPostergado {

    @EmbeddedId
    private CandidatoPostergadoId id = new CandidatoPostergadoId();

    /** Bloque liberado en cuya lista se posterga al candidato. */
    @MapsId("idOportunidad")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad")
    private Oportunidad oportunidad;

    /** Cita del candidato postergado (la que adelantaría). */
    @MapsId("idCita")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita")
    private Cita cita;

    /** Recepcionista que lo movió. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta_operador")
    private Cuenta cuentaOperador;

    /**
    Cuándo se movió. Ordena a los postergados entre sí, detrás de los no postergados; no altera la
    antigüedad de inscripción.
    */
    @Column(name = "postergado_en", nullable = false)
    private LocalDateTime postergadoEn;

    public CandidatoPostergadoId getId() {
        return id;
    }

    public void setId(CandidatoPostergadoId id) {
        this.id = id;
    }

    public Oportunidad getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(Oportunidad oportunidad) {
        this.oportunidad = oportunidad;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public Cuenta getCuentaOperador() {
        return cuentaOperador;
    }

    public void setCuentaOperador(Cuenta cuentaOperador) {
        this.cuentaOperador = cuentaOperador;
    }

    public LocalDateTime getPostergadoEn() {
        return postergadoEn;
    }

    public void setPostergadoEn(LocalDateTime postergadoEn) {
        this.postergadoEn = postergadoEn;
    }
}
