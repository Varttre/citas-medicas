package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Modalidad;

/**
Todo lo necesario para agendar una cita, venga del portal, de recepción o de la consulta del médico.

@param idPaciente         paciente que se atiende
@param idCuentaTitular    cuenta que agenda y sirve de contacto; null si recepción agenda sin cuenta
@param idMedico           médico que atiende
@param idEspecialidad     especialidad por la que se atiende (el médico debe ejercerla)
@param idTipoConsulta     tipo de consulta
@param inicio             fecha y hora de inicio, en múltiplos del incremento
@param duracionMin        duración en minutos, múltiplo del incremento
@param modalidad          presencial o teleconsulta
@param canal              por dónde llegó la reserva
@param idCuentaActor      cuenta de quien registra la reserva (paciente, recepción o médico)
@param idIndicacion       cita indicada que se está agendando; null si no viene de una indicación
@param preferencia        condiciones para adelantar la cita; null si no acepta que lo llamen
@param indicacionesAdmin  notas administrativas de recepción; puede ser null
@param autorizacionVerbal el paciente autorizó de palabra que el médico agende por él
*/
public record NuevaCitaComando(Long idPaciente, Long idCuentaTitular, Long idMedico, Short idEspecialidad,
        Short idTipoConsulta, LocalDateTime inicio, int duracionMin, Modalidad modalidad, CanalOrigen canal,
        Long idCuentaActor, Long idIndicacion, PreferenciaDatos preferencia, String indicacionesAdmin,
        boolean autorizacionVerbal) {
}
