package pe.consultorio.citas.service;

import org.springframework.stereotype.Service;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;

/**
A qué teléfono llama recepción por una cita. El contacto es la cuenta que la agendó: si la agendó una cuenta, se llama
al teléfono de su paciente propio (el titular); si la registró recepción sin cuenta, al del paciente.
*/
@Service
public class ContactoService {

    private final RegistroLlamadaRepository llamadas;

    public ContactoService(RegistroLlamadaRepository llamadas) {
        this.llamadas = llamadas;
    }

    /** Teléfono del paciente propio de la cuenta que agendó; si la cita no tiene cuenta, el del paciente. */
    public String telefonoDe(Cita cita) {
        Paciente contacto = cita.getCuentaTitular() != null && cita.getCuentaTitular().getPacientePropio() != null
            ? cita.getCuentaTitular().getPacientePropio() : cita.getPaciente();
        return contacto.getTelefono();
    }

    /** Indica si ese número ya se registró antes como inválido en alguna llamada. */
    public boolean telefonoInvalido(String telefono) {
        return telefono != null && llamadas.existsByTelefonoLlamadoAndResultado(telefono,
            ResultadoLlamada.NUMERO_INVALIDO);
    }
}
