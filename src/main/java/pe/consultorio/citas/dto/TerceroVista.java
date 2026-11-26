package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/**
Una persona que la cuenta puede agendar además de sí misma («Mis terceros» y «Para otra persona»). Muestra solo lo
que la cuenta misma ingresó: nombre, documento y edad; nunca el teléfono registrado del paciente.
*/
public record TerceroVista(Long idPaciente, String nombre, String tipoDocumento, String nroDocumento, int edad,
        LocalDateTime vinculadoEn) {
}
