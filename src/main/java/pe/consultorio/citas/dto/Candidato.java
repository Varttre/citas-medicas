package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Modalidad;

/**
Un paciente al que recepción puede llamar para ofrecerle un horario liberado, con los datos que se muestran en la
cola de cupos liberados.

@param idCita           cita que se adelantaría
@param idPaciente       paciente de esa cita
@param paciente         nombre para mostrar
@param telefonoContacto número al que se llama
@param telefonoInvalido ese número ya se marcó antes como inválido
@param alcance          alcance de su preferencia
@param minLlegadaMin    minutos que necesita para llegar
@param duracionMin      duración de su cita
@param residuoMin       minutos del bloque que quedarían libres si acepta
@param inscritoEn       desde cuándo acepta que lo llamen (antigüedad en la lista)
@param modalidad        modalidad de su cita
@param citaActual       inicio de su cita actual
@param inicioPropuesto  inicio que se le ofrece
@param puntaje          puntaje calculado; mayor = se llama antes
@param postergado       recepción lo dejó para el final de la lista
@param posicion         lugar en la lista, desde 1
*/
public record Candidato(Long idCita, Long idPaciente, String paciente, String telefonoContacto,
        boolean telefonoInvalido, AlcancePreferencia alcance, int minLlegadaMin, int duracionMin,
        int residuoMin, LocalDateTime inscritoEn, Modalidad modalidad, LocalDateTime citaActual,
        LocalDateTime inicioPropuesto, int puntaje, boolean postergado, int posicion) {
}
