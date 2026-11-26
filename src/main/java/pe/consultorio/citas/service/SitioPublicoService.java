package pe.consultorio.citas.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.EspecialidadPublica;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidad;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;

/** Lectura de lo que muestra la página pública del consultorio (sin sesión). No escribe nada. */
@Service
public class SitioPublicoService {

    private final EspecialidadRepository especialidades;
    private final MedicoEspecialidadRepository medicoEspecialidades;

    public SitioPublicoService(EspecialidadRepository especialidades,
            MedicoEspecialidadRepository medicoEspecialidades) {
        this.especialidades = especialidades;
        this.medicoEspecialidades = medicoEspecialidades;
    }

    /** Especialidades activas por nombre, cada una con sus médicos activos («Nombres Apellidos», por apellido). */
    @Transactional(readOnly = true)
    public List<EspecialidadPublica> especialidades() {
        Map<Short, List<Medico>> medicosPorEspecialidad = medicoEspecialidades.findAll().stream()
            .filter(me -> me.getMedico().isActivo())
            .collect(Collectors.groupingBy(me -> me.getEspecialidad().getIdEspecialidad(),
                Collectors.mapping(MedicoEspecialidad::getMedico, Collectors.toList())));
        return especialidades.findAll().stream()
            .filter(Especialidad::isActivo)
            .sorted(Comparator.comparing(Especialidad::getNombre))
            .map(e -> new EspecialidadPublica(e.getNombre(), e.getDescripcion(),
                medicosPorEspecialidad.getOrDefault(e.getIdEspecialidad(), List.of()).stream()
                    .sorted(Comparator.comparing(Medico::getApellidos))
                    .map(m -> m.getNombres() + " " + m.getApellidos())
                    .toList()))
            .toList();
    }
}
