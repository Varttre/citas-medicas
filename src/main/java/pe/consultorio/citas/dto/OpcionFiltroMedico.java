package pe.consultorio.citas.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
Un médico en un filtro, una sola vez aunque atienda varias especialidades. {@code idsEspecialidad} (separados por
espacios) permite que la pantalla oculte los médicos que no atienden la especialidad elegida, y al revés.
*/
public record OpcionFiltroMedico(Long idMedico, String nombre, String especialidades, String idsEspecialidad) {

    /** Agrupa las opciones médico–especialidad por médico, conservando el orden. */
    public static List<OpcionFiltroMedico> de(List<MedicoOpcion> opciones) {
        Map<Long, List<MedicoOpcion>> porMedico = opciones.stream()
            .collect(Collectors.groupingBy(MedicoOpcion::idMedico, LinkedHashMap::new, Collectors.toList()));
        return porMedico.values().stream()
            .map(l -> new OpcionFiltroMedico(l.get(0).idMedico(), l.get(0).nombre(),
                l.stream().map(MedicoOpcion::especialidad).collect(Collectors.joining(", ")),
                l.stream().map(o -> String.valueOf(o.idEspecialidad())).collect(Collectors.joining(" "))))
            .toList();
    }
}
