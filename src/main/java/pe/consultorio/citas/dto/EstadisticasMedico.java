package pe.consultorio.citas.dto;

import java.util.List;

/**
Las estadísticas del médico, solo con sus citas. Los valores son null cuando no hay datos para calcularlos.
*/
public record EstadisticasMedico(int ventana, List<DuracionTipo> duraciones, Double retrasoPromedioMin,
        Integer iniciadasTardePct, Integer noAsistenciaPct, long minutosPerdidos) {

    /**
    Duración real de un tipo de consulta con sus últimas {@code citas} (como máximo, la ventana). {@code variable}: la
    mediana y el P75 caen en ranuras distintas de la agenda.
    */
    public record DuracionTipo(String tipo, int configurada, Double mediana, Integer p75, int citas, boolean variable) {
    }
}
