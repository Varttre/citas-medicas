package pe.consultorio.citas.dto;

import java.util.List;

/**
Todo lo que muestra la pantalla «Mi horario».

@param versiones   versiones del horario, de la más reciente a la más antigua
@param excepciones ausencias que aún no terminaron, por fecha
@param duraciones  duración por tipo de consulta, por nombre del tipo
*/
public record HorarioMedicoDatos(List<VersionHorarioDatos> versiones, List<ExcepcionDatos> excepciones,
        List<DuracionDatos> duraciones) {
}
