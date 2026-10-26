package pe.consultorio.citas.service;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.entity.Cita;

/** Decide a qué teléfono se llama por una cita. */
@Service
public class ContactoService {

    /** Teléfono del paciente propio de la cuenta que agendó; si la cita no tiene cuenta, el del paciente. */
    public String telefonoDe(Cita cita) {
        throw new UnsupportedOperationException("Pendiente: A9");
    }

    /** Indica si ese número ya se registró antes como inválido en alguna llamada. */
    public boolean telefonoInvalido(String telefono) {
        throw new UnsupportedOperationException("Pendiente: A9");
    }
}
