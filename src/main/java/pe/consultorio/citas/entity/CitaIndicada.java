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

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Siguiente cita prescrita por el médico. Tabla cita_indicada. */
@Entity
@Table(name = "cita_indicada")
public class CitaIndicada {

    /** Identificador de la indicación. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_indicacion", updatable = false)
    private Long idIndicacion;

    /** Consulta en la que se indicó. De ella salen paciente y médico. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita_origen")
    private Cita citaOrigen;

    /** Tipo de consulta indicado; puede ser distinto del de la cita de origen. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_consulta")
    private TipoConsulta tipoConsulta;

    /** Múltiplo del incremento. */
    @Column(name = "duracion", nullable = false)
    private Short duracion;

    /** A partir de cuándo. */
    @Column(name = "fecha_recomendada", nullable = false)
    private LocalDate fechaRecomendada;

    /** Hasta cuándo sigue válida. */
    @Column(name = "vigente_hasta", nullable = false)
    private LocalDate vigenteHasta;

    /** Indicaciones del médico para esa siguiente cita. Opcional. */
    @Column(name = "indicaciones")
    private String indicaciones;

    /** PENDIENTE, AGENDADA, VENCIDA, DESCARTADA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoIndicacion estado;

    /** Cita que la cumplió. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita_generada")
    private Cita citaGenerada;

    /** Por qué se descartó (p. ej. «No le interesa»). Obligatorio si el estado es DESCARTADA. */
    @Column(name = "motivo_descarte", length = 255)
    private String motivoDescarte;

    /** Cuándo la registró el médico. */
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public Long getIdIndicacion() {
        return idIndicacion;
    }

    public void setIdIndicacion(Long idIndicacion) {
        this.idIndicacion = idIndicacion;
    }

    public Cita getCitaOrigen() {
        return citaOrigen;
    }

    public void setCitaOrigen(Cita citaOrigen) {
        this.citaOrigen = citaOrigen;
    }

    public TipoConsulta getTipoConsulta() {
        return tipoConsulta;
    }

    public void setTipoConsulta(TipoConsulta tipoConsulta) {
        this.tipoConsulta = tipoConsulta;
    }

    public Short getDuracion() {
        return duracion;
    }

    public void setDuracion(Short duracion) {
        this.duracion = duracion;
    }

    public LocalDate getFechaRecomendada() {
        return fechaRecomendada;
    }

    public void setFechaRecomendada(LocalDate fechaRecomendada) {
        this.fechaRecomendada = fechaRecomendada;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public void setIndicaciones(String indicaciones) {
        this.indicaciones = indicaciones;
    }

    public EstadoIndicacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoIndicacion estado) {
        this.estado = estado;
    }

    public Cita getCitaGenerada() {
        return citaGenerada;
    }

    public void setCitaGenerada(Cita citaGenerada) {
        this.citaGenerada = citaGenerada;
    }

    public String getMotivoDescarte() {
        return motivoDescarte;
    }

    public void setMotivoDescarte(String motivoDescarte) {
        this.motivoDescarte = motivoDescarte;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
