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

/**
Pacientes terceros para los que una cuenta agenda, con consentimiento. La cuenta solo ve las citas
que ella agendó. Tabla cuenta_paciente.
*/
@Entity
@Table(name = "cuenta_paciente")
public class CuentaPaciente {

    @EmbeddedId
    private CuentaPacienteId id = new CuentaPacienteId();

    /** Cuenta que agenda. */
    @MapsId("idCuenta")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    /** Paciente tercero. */
    @MapsId("idPaciente")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    /** Fecha de la declaración de consentimiento. */
    @Column(name = "consentimiento_en", nullable = false)
    private LocalDateTime consentimientoEn;

    /**
    Fecha en que el paciente retiró su consentimiento, registrada por recepción. Desde entonces la
    cuenta no puede agendarle citas nuevas; sus citas ya agendadas se mantienen. NULL = vigente.
    */
    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    public CuentaPacienteId getId() {
        return id;
    }

    public void setId(CuentaPacienteId id) {
        this.id = id;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public LocalDateTime getConsentimientoEn() {
        return consentimientoEn;
    }

    public void setConsentimientoEn(LocalDateTime consentimientoEn) {
        this.consentimientoEn = consentimientoEn;
    }

    public LocalDateTime getRevocadoEn() {
        return revocadoEn;
    }

    public void setRevocadoEn(LocalDateTime revocadoEn) {
        this.revocadoEn = revocadoEn;
    }
}
