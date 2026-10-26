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

/**
Resultado de cada llamada de las tres colas. Sin UPDATE ni DELETE. El número llamado es el contacto
de la cita (cuenta titular o, sin ella, el paciente). Tabla registro_llamada.
*/
@Entity
@Table(name = "registro_llamada")
public class RegistroLlamada {

    /** Identificador de la llamada. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_llamada", updatable = false)
    private Long idLlamada;

    /** Quién llamó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta_operador")
    private Cuenta cuentaOperador;

    /**
    Cola de recepción de la que sale la llamada: CONFIRMACION (confirmar una cita próxima),
    CUPO_LIBERADO (ofrecer un bloque liberado a un candidato), CITA_PENDIENTE (agendar una cita
    indicada por el médico). Decide qué referencias se llenan y qué resultados valen.
    */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cola", nullable = false, length = 15)
    private TipoCola tipoCola;

    /** Cita a confirmar, o cita principal del candidato. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita")
    private Cita cita;

    /** Solo en CUPO_LIBERADO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oportunidad")
    private Oportunidad oportunidad;

    /** Solo en CITA_PENDIENTE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_indicacion")
    private CitaIndicada indicacion;

    /** Posición del candidato en la lista recomendada. */
    @Column(name = "posicion_lista")
    private Short posicionLista;

    /**
    Número marcado, como estaba en ese momento. Con NUMERO_INVALIDO, el contacto se muestra como
    inválido mientras su teléfono vigente siga siendo este.
    */
    @Column(name = "telefono_llamado", nullable = false, length = 20)
    private String telefonoLlamado;

    /** Valores permitidos según la cola: ver chk_rl_resultado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private ResultadoLlamada resultado;

    /** Nota libre del operador sobre la llamada. */
    @Column(name = "observacion", length = 255)
    private String observacion;

    /** Cuándo se registró la llamada. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public Long getIdLlamada() {
        return idLlamada;
    }

    public void setIdLlamada(Long idLlamada) {
        this.idLlamada = idLlamada;
    }

    public Cuenta getCuentaOperador() {
        return cuentaOperador;
    }

    public void setCuentaOperador(Cuenta cuentaOperador) {
        this.cuentaOperador = cuentaOperador;
    }

    public TipoCola getTipoCola() {
        return tipoCola;
    }

    public void setTipoCola(TipoCola tipoCola) {
        this.tipoCola = tipoCola;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public Oportunidad getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(Oportunidad oportunidad) {
        this.oportunidad = oportunidad;
    }

    public CitaIndicada getIndicacion() {
        return indicacion;
    }

    public void setIndicacion(CitaIndicada indicacion) {
        this.indicacion = indicacion;
    }

    public Short getPosicionLista() {
        return posicionLista;
    }

    public void setPosicionLista(Short posicionLista) {
        this.posicionLista = posicionLista;
    }

    public String getTelefonoLlamado() {
        return telefonoLlamado;
    }

    public void setTelefonoLlamado(String telefonoLlamado) {
        this.telefonoLlamado = telefonoLlamado;
    }

    public ResultadoLlamada getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoLlamada resultado) {
        this.resultado = resultado;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
