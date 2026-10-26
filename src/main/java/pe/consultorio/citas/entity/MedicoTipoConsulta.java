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

/** Duración que usa cada médico por tipo de consulta. Tabla medico_tipo_consulta. */
@Entity
@Table(name = "medico_tipo_consulta")
public class MedicoTipoConsulta {

    @EmbeddedId
    private MedicoTipoConsultaId id = new MedicoTipoConsultaId();

    /** Médico. */
    @MapsId("idMedico")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    @MapsId("idTipoConsulta")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_consulta")
    private TipoConsulta tipoConsulta;

    /** La que usa el sistema para agendar. */
    @Column(name = "duracion_configurada", nullable = false)
    private Short duracionConfigurada;

    /** Último valor sugerido y rechazado; no se repite mientras la mediana no cambie de ranura. */
    @Column(name = "sugerencia_rechazada")
    private Short sugerenciaRechazada;

    /**
    Con «Recordar después», la sugerencia no vuelve a mostrarse antes de esta fecha (ahora +
    dias_recordar_sugerencia). NULL = sin posponer.
    */
    @Column(name = "recordar_desde")
    private LocalDateTime recordarDesde;

    public MedicoTipoConsultaId getId() {
        return id;
    }

    public void setId(MedicoTipoConsultaId id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public TipoConsulta getTipoConsulta() {
        return tipoConsulta;
    }

    public void setTipoConsulta(TipoConsulta tipoConsulta) {
        this.tipoConsulta = tipoConsulta;
    }

    public Short getDuracionConfigurada() {
        return duracionConfigurada;
    }

    public void setDuracionConfigurada(Short duracionConfigurada) {
        this.duracionConfigurada = duracionConfigurada;
    }

    public Short getSugerenciaRechazada() {
        return sugerenciaRechazada;
    }

    public void setSugerenciaRechazada(Short sugerenciaRechazada) {
        this.sugerenciaRechazada = sugerenciaRechazada;
    }

    public LocalDateTime getRecordarDesde() {
        return recordarDesde;
    }

    public void setRecordarDesde(LocalDateTime recordarDesde) {
        this.recordarDesde = recordarDesde;
    }
}
