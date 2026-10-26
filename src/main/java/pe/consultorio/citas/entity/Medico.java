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

/** Profesional que atiende. Tabla medico. */
@Entity
@Table(name = "medico")
public class Medico {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medico", updatable = false)
    private Long idMedico;

    /** Cuenta de acceso del médico. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta")
    private Cuenta cuenta;

    /** Nombres del médico, como se muestran al paciente en la búsqueda y en sus citas. */
    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    /** Apellidos del médico. */
    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    /**
    El médico recibe citas nuevas y aparece en las búsquedas. Un médico inactivo puede seguir
    entrando con su cuenta para ver su historial; el acceso lo controla CUENTA.estado.
    */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    public Long getIdMedico() {
        return idMedico;
    }

    public void setIdMedico(Long idMedico) {
        this.idMedico = idMedico;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public void setCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
