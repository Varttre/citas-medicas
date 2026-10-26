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

import java.time.LocalDateTime;

/** Reserva provisional mientras el usuario completa el formulario. Tabla bloqueo_temporal. */
@Entity
@Table(name = "bloqueo_temporal")
public class BloqueoTemporal {

    /** Identificador del bloqueo. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bloqueo", updatable = false)
    private Long idBloqueo;

    /** Médico cuyo horario se retiene. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /** Quien tiene el bloqueo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    /** Inicio del horario retenido. */
    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    /** Fin del horario retenido. */
    @Column(name = "fin", nullable = false)
    private LocalDateTime fin;

    /** Cuándo se libera solo si el usuario no confirma (contador visible; dura bloqueo_temporal_min). */
    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    public Long getIdBloqueo() {
        return idBloqueo;
    }

    public void setIdBloqueo(Long idBloqueo) {
        this.idBloqueo = idBloqueo;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
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

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }
}
