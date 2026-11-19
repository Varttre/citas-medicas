package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/** Una cita en la agenda del día (recepción o médico). {@code ajusteRecepcion}: el tipo deja a recepción cambiar la duración. */
public record CitaAgenda(Long idCita, Long idMedico, String medico, String especialidad, LocalDateTime inicio,
        LocalDateTime fin, String paciente, String documento, String tipo, String modalidad, String estadoCodigo,
        String estado, boolean atencionIniciada, boolean ajusteRecepcion) {

    public long duracionMin() {
        return java.time.Duration.between(inicio, fin).toMinutes();
    }

    public boolean vigente() {
        return "PROGRAMADA".equals(estadoCodigo) || "CONFIRMADA".equals(estadoCodigo);
    }

    public boolean asistio() {
        return "ASISTIO".equals(estadoCodigo);
    }
}
