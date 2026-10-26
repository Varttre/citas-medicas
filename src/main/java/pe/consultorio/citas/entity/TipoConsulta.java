package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo configurable de tipos de consulta. Tabla tipo_consulta. */
@Entity
@Table(name = "tipo_consulta")
public class TipoConsulta {

    /** Identificador del tipo de consulta. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_consulta", updatable = false)
    private Short idTipoConsulta;

    /** Nombre del tipo (p. ej. Consulta nueva, Control, Procedimiento). Único. */
    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    /** Múltiplo del incremento. */
    @Column(name = "duracion_defecto", nullable = false)
    private Short duracionDefecto;

    /** NULL = hereda el global. */
    @Column(name = "incremento_min")
    private Short incrementoMin;

    /** El médico puede ajustar la duración. */
    @Column(name = "ajuste_medico", nullable = false)
    private boolean ajusteMedico;

    /** Recepción puede ajustar la duración. */
    @Column(name = "ajuste_recepcion", nullable = false)
    private boolean ajusteRecepcion;

    /** Visible en el portal del paciente. */
    @Column(name = "reservable_web", nullable = false)
    private boolean reservableWeb;

    /** PRESENCIAL, TELECONSULTA, AMBAS. */
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_permitida", nullable = false, length = 12)
    private ModalidadPermitida modalidadPermitida;

    /** Si el tipo se puede usar en citas nuevas. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    public Short getIdTipoConsulta() {
        return idTipoConsulta;
    }

    public void setIdTipoConsulta(Short idTipoConsulta) {
        this.idTipoConsulta = idTipoConsulta;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Short getDuracionDefecto() {
        return duracionDefecto;
    }

    public void setDuracionDefecto(Short duracionDefecto) {
        this.duracionDefecto = duracionDefecto;
    }

    public Short getIncrementoMin() {
        return incrementoMin;
    }

    public void setIncrementoMin(Short incrementoMin) {
        this.incrementoMin = incrementoMin;
    }

    public boolean isAjusteMedico() {
        return ajusteMedico;
    }

    public void setAjusteMedico(boolean ajusteMedico) {
        this.ajusteMedico = ajusteMedico;
    }

    public boolean isAjusteRecepcion() {
        return ajusteRecepcion;
    }

    public void setAjusteRecepcion(boolean ajusteRecepcion) {
        this.ajusteRecepcion = ajusteRecepcion;
    }

    public boolean isReservableWeb() {
        return reservableWeb;
    }

    public void setReservableWeb(boolean reservableWeb) {
        this.reservableWeb = reservableWeb;
    }

    public ModalidadPermitida getModalidadPermitida() {
        return modalidadPermitida;
    }

    public void setModalidadPermitida(ModalidadPermitida modalidadPermitida) {
        this.modalidadPermitida = modalidadPermitida;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
