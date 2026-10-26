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

/** Bitácora persistente. Sin UPDATE ni DELETE. Tabla auditoria. */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    /** Identificador del registro de auditoría. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria", updatable = false)
    private Long idAuditoria;

    /** NULL para acciones automáticas del sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    /**
    Qué ocurrió. Valores previstos: LOGIN_OK, LOGIN_FALLIDO, CUENTA_BLOQUEADA, CONFIG_CAMBIO,
    AUTORIZACION_VERBAL, PREFERENCIA_CAMBIO, CAMBIO_HORARIO_RECHAZADO, VINCULO_PACIENTE.
    */
    @Column(name = "accion", nullable = false, length = 40)
    private String accion;

    /** Tabla afectada. */
    @Column(name = "entidad", nullable = false, length = 40)
    private String entidad;

    /**
    Clave de la fila afectada como texto (admite claves compuestas, p. ej. "12|3", y de texto, p.
    ej. "RETIRO"). NULL cuando no hay fila, p. ej. un inicio de sesión con un correo inexistente.
    */
    @Column(name = "id_registro", length = 60)
    private String idRegistro;

    /** Valores anterior y nuevo. */
    @Column(name = "detalle")
    private String detalle;

    /**
    Motivo declarado cuando la acción lo requiere (p. ej. un cambio de configuración o un
    desbloqueo).
    */
    @Column(name = "motivo", length = 255)
    private String motivo;

    /** Cuándo ocurrió la acción. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public Long getIdAuditoria() {
        return idAuditoria;
    }

    public void setIdAuditoria(Long idAuditoria) {
        this.idAuditoria = idAuditoria;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getIdRegistro() {
        return idRegistro;
    }

    public void setIdRegistro(String idRegistro) {
        this.idRegistro = idRegistro;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
