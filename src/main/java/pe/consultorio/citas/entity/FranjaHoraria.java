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

import java.time.LocalTime;

/** Lapsos de atención de una versión. Tabla franja_horaria. */
@Entity
@Table(name = "franja_horaria")
public class FranjaHoraria {

    /** Identificador de la franja. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_franja", updatable = false)
    private Long idFranja;

    /**
    Versión de horario a la que pertenece. Un mismo día puede tener varias franjas (p. ej. mañana y
    tarde).
    */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_horario_version")
    private HorarioVersion horarioVersion;

    /** 1 = lunes … 7 = domingo. */
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    /** Múltiplo de 15 min. */
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    /** Múltiplo de 15 min. */
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    public Long getIdFranja() {
        return idFranja;
    }

    public void setIdFranja(Long idFranja) {
        this.idFranja = idFranja;
    }

    public HorarioVersion getHorarioVersion() {
        return horarioVersion;
    }

    public void setHorarioVersion(HorarioVersion horarioVersion) {
        this.horarioVersion = horarioVersion;
    }

    public Short getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(Short diaSemana) {
        this.diaSemana = diaSemana;
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
}
