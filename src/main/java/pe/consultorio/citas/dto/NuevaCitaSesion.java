package pe.consultorio.citas.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import pe.consultorio.citas.entity.Modalidad;

/**
Lo que el paciente va eligiendo en la nueva cita, guardado en la sesión entre un paso y el siguiente.
{@code inicio} es null hasta que elige un horario. {@code idIndicacion}: la cita indicada por un médico que se está
agendando (tipo y duración salen de ella); null si es una consulta nueva.
*/
public record NuevaCitaSesion(Long idMedico, String medico, Short idEspecialidad, String especialidad,
        Short idTipoConsulta, String tipo, int duracionMin, Modalidad modalidad, LocalDateTime inicio,
        Long idIndicacion) implements Serializable {

    public NuevaCitaSesion conHorario(LocalDateTime nuevoInicio) {
        return new NuevaCitaSesion(idMedico, medico, idEspecialidad, especialidad, idTipoConsulta, tipo, duracionMin,
            modalidad, nuevoInicio, idIndicacion);
    }

    public NuevaCitaSesion conModalidad(Modalidad nueva) {
        return new NuevaCitaSesion(idMedico, medico, idEspecialidad, especialidad, idTipoConsulta, tipo, duracionMin,
            nueva, inicio, idIndicacion);
    }

    public LocalDateTime fin() {
        return inicio == null ? null : inicio.plusMinutes(duracionMin);
    }
}
