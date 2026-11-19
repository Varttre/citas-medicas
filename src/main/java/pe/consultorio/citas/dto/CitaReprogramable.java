package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

import pe.consultorio.citas.entity.Modalidad;

/**
Lo que necesita la pantalla de reprogramar: la cita actual, lo que no cambia (médico, tipo, duración, modalidad), el
teléfono al que llamará el consultorio y la preferencia de disponibilidad inmediata vigente (null si no tiene).
*/
public record CitaReprogramable(Long idCita, Long idMedico, String medico, String especialidad, String tipo,
        int duracionMin, Modalidad modalidad, LocalDateTime inicio, LocalDateTime fin, String paciente,
        String estado, String telefono, PreferenciaDatos preferencia) {
}
