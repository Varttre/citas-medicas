package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Roles del sistema. Tabla rol. */
@Entity
@Table(name = "rol")
public class Rol {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol", updatable = false)
    private Short idRol;

    /** Nombre del rol. Valores posibles: los de NombreRol. */
    @Enumerated(EnumType.STRING)
    @Column(name = "nombre", nullable = false, length = 30)
    private NombreRol nombre;

    public Short getIdRol() {
        return idRol;
    }

    public void setIdRol(Short idRol) {
        this.idRol = idRol;
    }

    public NombreRol getNombre() {
        return nombre;
    }

    public void setNombre(NombreRol nombre) {
        this.nombre = nombre;
    }
}
