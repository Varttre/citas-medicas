package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clave primaria compuesta de CuentaPaciente (id_cuenta, id_paciente). */
@Embeddable
public class CuentaPacienteId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_cuenta")
    private Long idCuenta;

    @Column(name = "id_paciente")
    private Long idPaciente;

    public CuentaPacienteId() {
    }

    public CuentaPacienteId(Long idCuenta, Long idPaciente) {
        this.idCuenta = idCuenta;
        this.idPaciente = idPaciente;
    }

    public Long getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(Long idCuenta) {
        this.idCuenta = idCuenta;
    }

    public Long getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Long idPaciente) {
        this.idPaciente = idPaciente;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CuentaPacienteId otro)) {
            return false;
        }
        return Objects.equals(idCuenta, otro.idCuenta)
            && Objects.equals(idPaciente, otro.idPaciente);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCuenta, idPaciente);
    }
}
