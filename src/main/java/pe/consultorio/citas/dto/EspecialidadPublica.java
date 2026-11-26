package pe.consultorio.citas.dto;

import java.util.List;

/** Una especialidad activa tal como se muestra en la página pública, con los nombres de sus médicos activos. */
public record EspecialidadPublica(String nombre, String descripcion, List<String> medicos) {

    /** Icono de Bootstrap Icons para la especialidad; uno genérico si no es de las conocidas. */
    public String icono() {
        String n = nombre.toLowerCase();
        if (n.contains("cardio")) return "bi-heart-pulse";
        if (n.contains("pediat")) return "bi-emoji-smile";
        if (n.contains("dermat")) return "bi-bandaid";
        if (n.contains("gineco")) return "bi-gender-female";
        if (n.contains("general")) return "bi-hospital";
        return "bi-clipboard2-pulse";
    }
}
