package pe.consultorio.citas.dto;

import java.io.Serializable;
import java.time.LocalDate;

import pe.consultorio.citas.entity.Parentesco;
import pe.consultorio.citas.entity.TipoDocumento;

/** Datos de la otra persona para quien una cuenta agenda una cita. El teléfono es opcional y nunca se guarda. */
public record TerceroForm(TipoDocumento tipoDocumento, String nroDocumento, String nombres, String apellidos,
        LocalDate fechaNacimiento, String telefono, Boolean consentimiento, Parentesco parentesco)
        implements Serializable {

    /** Sin parentesco (es opcional). */
    public TerceroForm(TipoDocumento tipoDocumento, String nroDocumento, String nombres, String apellidos,
            LocalDate fechaNacimiento, String telefono, Boolean consentimiento) {
        this(tipoDocumento, nroDocumento, nombres, apellidos, fechaNacimiento, telefono, consentimiento, null);
    }
}
