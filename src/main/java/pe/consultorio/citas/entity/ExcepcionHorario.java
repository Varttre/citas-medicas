package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

/** Bloqueo puntual sobre fechas concretas. Tabla excepcion_horario. */
@Entity
@Table(name = "excepcion_horario")
public class ExcepcionHorario {

    /** Identificador de la excepción. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_excepcion", updatable = false)
    private Long idExcepcion;

    /** Médico afectado. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /** Primer día bloqueado. */
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Último día bloqueado; igual a fecha_inicio si es un solo día. */
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /** NULL = día completo. */
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    /** NULL = día completo. */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    /** Razón del bloqueo (vacaciones, congreso, trámite). Obligatoria. */
    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    public Long getIdExcepcion() {
        return idExcepcion;
    }

    public void setIdExcepcion(Long idExcepcion) {
        this.idExcepcion = idExcepcion;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
