package pe.consultorio.citas.exception;

import java.util.List;

import pe.consultorio.citas.dto.CitaAfectada;

/**
Un cambio de horario dejaría fuera citas ya agendadas. Lleva la lista de esas citas, ordenada por fecha, para que la
pantalla muestre cuáles hay que reprogramar primero.
*/
public class HorarioConCitasException extends ReglaNegocioException {

    private final transient List<CitaAfectada> citas;

    public HorarioConCitasException(String mensajeParaUsuario, List<CitaAfectada> citas) {
        super(mensajeParaUsuario);
        this.citas = List.copyOf(citas);
    }

    public List<CitaAfectada> getCitas() {
        return citas;
    }
}
