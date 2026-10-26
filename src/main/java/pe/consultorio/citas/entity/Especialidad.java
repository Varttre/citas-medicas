package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo de especialidades. Tabla especialidad. */
@Entity
@Table(name = "especialidad")
public class Especialidad {

    /** Identificador de la especialidad. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_especialidad", updatable = false)
    private Short idEspecialidad;

    /** Nombre de la especialidad (p. ej. Cardiología). Único. */
    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    /** Descripción breve para el paciente. */
    @Column(name = "descripcion", length = 255)
    private String descripcion;

    /** Si la especialidad se ofrece para agendar citas nuevas. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    public Short getIdEspecialidad() {
        return idEspecialidad;
    }

    public void setIdEspecialidad(Short idEspecialidad) {
        this.idEspecialidad = idEspecialidad;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
