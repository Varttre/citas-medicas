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

/** Historial de la cita. Sin UPDATE ni DELETE. Tabla cita_evento. */
@Entity
@Table(name = "cita_evento")
public class CitaEvento {

    /** Identificador del evento. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento", updatable = false)
    private Long idEvento;

    /** Cita a la que pertenece el evento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita")
    private Cita cita;

    /** Quién actuó. NULL = acción automática. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    /** Obligatoria si estado_nuevo = CONFIRMADA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_llamada")
    private RegistroLlamada llamada;

    /**
    CREACION, CAMBIO_ESTADO, REPROGRAMACION, AJUSTE_DURACION, LLEGADA, INICIO_ATENCION,
    FIN_ATENCION.
    */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private TipoEvento tipoEvento;

    /** Estado antes del evento. NULL en la creación y en eventos que no cambian el estado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 25)
    private EstadoCita estadoAnterior;

    /** Estado después del evento. NULL en eventos que no cambian el estado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 25)
    private EstadoCita estadoNuevo;

    /** Inicio del horario antes de una reprogramación o de un ajuste de duración. */
    @Column(name = "inicio_anterior")
    private LocalDateTime inicioAnterior;

    /** Fin del horario anterior. */
    @Column(name = "fin_anterior")
    private LocalDateTime finAnterior;

    /** Inicio del horario nuevo. */
    @Column(name = "inicio_nuevo")
    private LocalDateTime inicioNuevo;

    /** Fin del horario nuevo. */
    @Column(name = "fin_nuevo")
    private LocalDateTime finNuevo;

    /**
    Motivo del cambio. Obligatorio al cancelar por la clínica y en un retiro (CHECK); en las
    cancelaciones que registra recepción lo exige el servicio.
    */
    @Column(name = "motivo", length = 255)
    private String motivo;

    /** Cuándo ocurrió el evento. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public Long getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(Long idEvento) {
        this.idEvento = idEvento;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
    }

    public RegistroLlamada getLlamada() {
        return llamada;
    }

    public void setLlamada(RegistroLlamada llamada) {
        this.llamada = llamada;
    }

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEvento tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public EstadoCita getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoCita estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoCita getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(EstadoCita estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public LocalDateTime getInicioAnterior() {
        return inicioAnterior;
    }

    public void setInicioAnterior(LocalDateTime inicioAnterior) {
        this.inicioAnterior = inicioAnterior;
    }

    public LocalDateTime getFinAnterior() {
        return finAnterior;
    }

    public void setFinAnterior(LocalDateTime finAnterior) {
        this.finAnterior = finAnterior;
    }

    public LocalDateTime getInicioNuevo() {
        return inicioNuevo;
    }

    public void setInicioNuevo(LocalDateTime inicioNuevo) {
        this.inicioNuevo = inicioNuevo;
    }

    public LocalDateTime getFinNuevo() {
        return finNuevo;
    }

    public void setFinNuevo(LocalDateTime finNuevo) {
        this.finNuevo = finNuevo;
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
