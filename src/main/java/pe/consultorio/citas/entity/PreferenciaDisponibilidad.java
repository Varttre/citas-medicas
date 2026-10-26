package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Consentimiento y condiciones para adelantar una cita. Tabla preferencia_disponibilidad. */
@Entity
@Table(name = "preferencia_disponibilidad")
public class PreferenciaDisponibilidad {

    /** Identificador de la preferencia. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia", updatable = false)
    private Long idPreferencia;

    /** Cita que el paciente quiere adelantar. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita")
    private Cita cita;

    /** MEDICO, ESPECIALIDAD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 12)
    private AlcancePreferencia alcance;

    /**
    Tiempo mínimo que el paciente necesita para llegar, en minutos. La pantalla lo pide como valor +
    unidad (min u h) y lo convierte; en teleconsulta se sugiere 0.
    */
    @Column(name = "min_llegada_min", nullable = false)
    private Short minLlegadaMin;

    /** Días antes de su cita en que acepta adelantar. */
    @Column(name = "dias_previos", nullable = false)
    private Short diasPrevios;

    @Column(name = "inscrito_en", nullable = false)
    private LocalDateTime inscritoEn;

    /**
    Última aceptación o edición del consentimiento. Cada cambio queda además en AUDITORIA
    (PREFERENCIA_CAMBIO).
    */
    @Column(name = "consentido_en", nullable = false)
    private LocalDateTime consentidoEn;

    /**
    Cuándo el paciente retiró el consentimiento, desde Mis citas o en recepción. NULL = vigente: la
    cita puede recibir ofertas de adelanto.
    */
    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    public Long getIdPreferencia() {
        return idPreferencia;
    }

    public void setIdPreferencia(Long idPreferencia) {
        this.idPreferencia = idPreferencia;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public AlcancePreferencia getAlcance() {
        return alcance;
    }

    public void setAlcance(AlcancePreferencia alcance) {
        this.alcance = alcance;
    }

    public Short getMinLlegadaMin() {
        return minLlegadaMin;
    }

    public void setMinLlegadaMin(Short minLlegadaMin) {
        this.minLlegadaMin = minLlegadaMin;
    }

    public Short getDiasPrevios() {
        return diasPrevios;
    }

    public void setDiasPrevios(Short diasPrevios) {
        this.diasPrevios = diasPrevios;
    }

    public LocalDateTime getInscritoEn() {
        return inscritoEn;
    }

    public void setInscritoEn(LocalDateTime inscritoEn) {
        this.inscritoEn = inscritoEn;
    }

    public LocalDateTime getConsentidoEn() {
        return consentidoEn;
    }

    public void setConsentidoEn(LocalDateTime consentidoEn) {
        this.consentidoEn = consentidoEn;
    }

    public LocalDateTime getRevocadoEn() {
        return revocadoEn;
    }

    public void setRevocadoEn(LocalDateTime revocadoEn) {
        this.revocadoEn = revocadoEn;
    }
}
