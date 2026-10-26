package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Parámetros globales. Una sola fila. Tabla parametro_consultorio. */
@Entity
@Table(name = "parametro_consultorio")
public class ParametroConsultorio {

    /** Siempre 1 (CHECK). */
    @Id
    @Column(name = "id_parametro", nullable = false)
    private Short idParametro;

    /** Rejilla temporal. Sugerido: 15. */
    @Column(name = "incremento_min", nullable = false)
    private Short incrementoMin;

    /** NUNCA, BLANDA, DURA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "politica_proteccion", nullable = false, length = 10)
    private PoliticaProteccion politicaProteccion;

    /** Minutos por día protegidos para consultas largas. */
    @Column(name = "minutos_protegidos", nullable = false)
    private Short minutosProtegidos;

    /** Horas antes de la cita en que caduca la protección blanda. */
    @Column(name = "ventana_liberacion_h", nullable = false)
    private Short ventanaLiberacionH;

    /** Duración del bloqueo de un horario en selección. */
    @Column(name = "bloqueo_temporal_min", nullable = false)
    private Short bloqueoTemporalMin;

    /** Citas de la ventana móvil. Sugerido: 30. */
    @Column(name = "n_ventana_estimacion", nullable = false)
    private Short nVentanaEstimacion;

    /** Horas antes en que una cita entra a la cola de confirmación. */
    @Column(name = "anticipacion_confirm_h", nullable = false)
    private Short anticipacionConfirmH;

    /** Vigencia por defecto de una cita indicada. */
    @Column(name = "vigencia_indicada_dias", nullable = false)
    private Short vigenciaIndicadaDias;

    /**
    Si la ventana de cancelación del paciente muestra un aviso de penalización por cancelación
    tardía.
    */
    @Column(name = "mostrar_penalizacion", nullable = false)
    private boolean mostrarPenalizacion;

    /** Texto de ese aviso. Obligatorio si mostrar_penalizacion es verdadero. */
    @Column(name = "texto_penalizacion", length = 255)
    private String textoPenalizacion;

    @Column(name = "max_intentos_login", nullable = false)
    private Short maxIntentosLogin;

    /** Duración de ese bloqueo temporal, en minutos. */
    @Column(name = "minutos_bloqueo_login", nullable = false)
    private Short minutosBloqueoLogin;

    @Column(name = "whatsapp_numero", length = 20)
    private String whatsappNumero;

    @Column(name = "dias_recordar_sugerencia", nullable = false)
    private Short diasRecordarSugerencia;

    /**
    Tipo de consulta con el que continúa [Agendar una consulta nueva] en el portal. Debe ser
    reservable por web (lo valida el servicio).
    */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_web_defecto")
    private TipoConsulta tipoWebDefecto;

    public Short getIdParametro() {
        return idParametro;
    }

    public void setIdParametro(Short idParametro) {
        this.idParametro = idParametro;
    }

    public Short getIncrementoMin() {
        return incrementoMin;
    }

    public void setIncrementoMin(Short incrementoMin) {
        this.incrementoMin = incrementoMin;
    }

    public PoliticaProteccion getPoliticaProteccion() {
        return politicaProteccion;
    }

    public void setPoliticaProteccion(PoliticaProteccion politicaProteccion) {
        this.politicaProteccion = politicaProteccion;
    }

    public Short getMinutosProtegidos() {
        return minutosProtegidos;
    }

    public void setMinutosProtegidos(Short minutosProtegidos) {
        this.minutosProtegidos = minutosProtegidos;
    }

    public Short getVentanaLiberacionH() {
        return ventanaLiberacionH;
    }

    public void setVentanaLiberacionH(Short ventanaLiberacionH) {
        this.ventanaLiberacionH = ventanaLiberacionH;
    }

    public Short getBloqueoTemporalMin() {
        return bloqueoTemporalMin;
    }

    public void setBloqueoTemporalMin(Short bloqueoTemporalMin) {
        this.bloqueoTemporalMin = bloqueoTemporalMin;
    }

    public Short getNVentanaEstimacion() {
        return nVentanaEstimacion;
    }

    public void setNVentanaEstimacion(Short nVentanaEstimacion) {
        this.nVentanaEstimacion = nVentanaEstimacion;
    }

    public Short getAnticipacionConfirmH() {
        return anticipacionConfirmH;
    }

    public void setAnticipacionConfirmH(Short anticipacionConfirmH) {
        this.anticipacionConfirmH = anticipacionConfirmH;
    }

    public Short getVigenciaIndicadaDias() {
        return vigenciaIndicadaDias;
    }

    public void setVigenciaIndicadaDias(Short vigenciaIndicadaDias) {
        this.vigenciaIndicadaDias = vigenciaIndicadaDias;
    }

    public boolean isMostrarPenalizacion() {
        return mostrarPenalizacion;
    }

    public void setMostrarPenalizacion(boolean mostrarPenalizacion) {
        this.mostrarPenalizacion = mostrarPenalizacion;
    }

    public String getTextoPenalizacion() {
        return textoPenalizacion;
    }

    public void setTextoPenalizacion(String textoPenalizacion) {
        this.textoPenalizacion = textoPenalizacion;
    }

    public Short getMaxIntentosLogin() {
        return maxIntentosLogin;
    }

    public void setMaxIntentosLogin(Short maxIntentosLogin) {
        this.maxIntentosLogin = maxIntentosLogin;
    }

    public Short getMinutosBloqueoLogin() {
        return minutosBloqueoLogin;
    }

    public void setMinutosBloqueoLogin(Short minutosBloqueoLogin) {
        this.minutosBloqueoLogin = minutosBloqueoLogin;
    }

    public String getWhatsappNumero() {
        return whatsappNumero;
    }

    public void setWhatsappNumero(String whatsappNumero) {
        this.whatsappNumero = whatsappNumero;
    }

    public Short getDiasRecordarSugerencia() {
        return diasRecordarSugerencia;
    }

    public void setDiasRecordarSugerencia(Short diasRecordarSugerencia) {
        this.diasRecordarSugerencia = diasRecordarSugerencia;
    }

    public TipoConsulta getTipoWebDefecto() {
        return tipoWebDefecto;
    }

    public void setTipoWebDefecto(TipoConsulta tipoWebDefecto) {
        this.tipoWebDefecto = tipoWebDefecto;
    }
}
