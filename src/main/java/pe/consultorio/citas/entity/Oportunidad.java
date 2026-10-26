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

/** Bloque liberado. Hecho histórico, no un slot. Tabla oportunidad. */
@Entity
@Table(name = "oportunidad")
public class Oportunidad {

    /** Identificador de la oportunidad. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oportunidad", updatable = false)
    private Long idOportunidad;

    /** Cita cancelada o reprogramada que liberó el horario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita_origen")
    private Cita citaOrigen;

    /** Médico cuya agenda liberó el bloque. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /**
    Oportunidad anterior de la que esta sale: el remanente de un bloque cubierto en parte, o la que
    queda REEMPLAZADA al fusionarse con un hueco contiguo. Un solo padre.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oportunidad_padre")
    private Oportunidad oportunidadPadre;

    /** Bloque coalescido al generarse. */
    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    /** Fin del bloque liberado. */
    @Column(name = "fin", nullable = false)
    private LocalDateTime fin;

    /** ABIERTA, CUBIERTA, EXPIRADA, REEMPLAZADA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoOportunidad estado;

    /** Cuándo se liberó el bloque. */
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    /** Cuándo dejó de estar ABIERTA (cubierta, expirada o reemplazada). */
    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita_cobertura")
    private Cita citaCobertura;

    public Long getIdOportunidad() {
        return idOportunidad;
    }

    public void setIdOportunidad(Long idOportunidad) {
        this.idOportunidad = idOportunidad;
    }

    public Cita getCitaOrigen() {
        return citaOrigen;
    }

    public void setCitaOrigen(Cita citaOrigen) {
        this.citaOrigen = citaOrigen;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Oportunidad getOportunidadPadre() {
        return oportunidadPadre;
    }

    public void setOportunidadPadre(Oportunidad oportunidadPadre) {
        this.oportunidadPadre = oportunidadPadre;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFin() {
        return fin;
    }

    public void setFin(LocalDateTime fin) {
        this.fin = fin;
    }

    public EstadoOportunidad getEstado() {
        return estado;
    }

    public void setEstado(EstadoOportunidad estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }

    public LocalDateTime getCerradaEn() {
        return cerradaEn;
    }

    public void setCerradaEn(LocalDateTime cerradaEn) {
        this.cerradaEn = cerradaEn;
    }

    public Cita getCitaCobertura() {
        return citaCobertura;
    }

    public void setCitaCobertura(Cita citaCobertura) {
        this.citaCobertura = citaCobertura;
    }
}
