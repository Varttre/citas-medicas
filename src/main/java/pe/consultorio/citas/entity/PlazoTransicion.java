package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla plazo_transicion. Tabla plazo_transicion. */
@Entity
@Table(name = "plazo_transicion")
public class PlazoTransicion {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "transicion", nullable = false, length = 25)
    private Transicion transicion;

    /**
    Desde cuándo se permite, en minutos relativos a la hora de inicio de la cita; negativo = antes.
    NULL = sin límite inferior. Configurable por el administrador.
    */
    @Column(name = "minutos_desde")
    private Short minutosDesde;

    /**
    Hasta cuándo se permite, en minutos relativos a la hora de inicio. NULL = sin límite, o el que
    fije hasta_fin_dia. Configurable por el administrador.
    */
    @Column(name = "minutos_hasta")
    private Short minutosHasta;

    /**
    Límite fijo por calendario: 0 = fin del día de la cita, 1 = fin del día siguiente. Regla del
    sistema, no editable. Excluye minutos_hasta.
    */
    @Column(name = "hasta_fin_dia")
    private Short hastaFinDia;

    public Transicion getTransicion() {
        return transicion;
    }

    public void setTransicion(Transicion transicion) {
        this.transicion = transicion;
    }

    public Short getMinutosDesde() {
        return minutosDesde;
    }

    public void setMinutosDesde(Short minutosDesde) {
        this.minutosDesde = minutosDesde;
    }

    public Short getMinutosHasta() {
        return minutosHasta;
    }

    public void setMinutosHasta(Short minutosHasta) {
        this.minutosHasta = minutosHasta;
    }

    public Short getHastaFinDia() {
        return hastaFinDia;
    }

    public void setHastaFinDia(Short hastaFinDia) {
        this.hastaFinDia = hastaFinDia;
    }
}
