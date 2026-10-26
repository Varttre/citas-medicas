package pe.consultorio.citas.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Versión estructural del horario con vigencia. Tabla horario_version. */
@Entity
@Table(name = "horario_version")
public class HorarioVersion {

    /** Identificador de la versión de horario. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario_version", updatable = false)
    private Long idHorarioVersion;

    /** Médico al que pertenece el horario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /** Desde qué fecha rige esta versión. */
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** NULL = versión vigente. */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Franjas de atención de esta versión. Se guardan y se borran junto con la versión. */
    @OneToMany(mappedBy = "horarioVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FranjaHoraria> franjas = new ArrayList<>();

    public Long getIdHorarioVersion() {
        return idHorarioVersion;
    }

    public void setIdHorarioVersion(Long idHorarioVersion) {
        this.idHorarioVersion = idHorarioVersion;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(LocalDate vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public List<FranjaHoraria> getFranjas() {
        return franjas;
    }

    public void setFranjas(List<FranjaHoraria> franjas) {
        this.franjas = franjas;
    }

    /** Añade una franja y deja enlazada su versión, para que ambas caras de la relación coincidan. */
    public void agregarFranja(FranjaHoraria franja) {
        franja.setHorarioVersion(this);
        franjas.add(franja);
    }
}
