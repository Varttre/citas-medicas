package pe.consultorio.citas.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaAgenda;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.PacienteRepository;

/**
Consultas de agenda para recepción (todos los médicos) y para el médico (solo las suyas): por día, por rango de días
o por paciente, con filtros de médico, especialidad y estado.
*/
@Service
@Transactional(readOnly = true)
public class AgendaService {

    /** Rango más largo que se puede pedir de una vez. */
    public static final int MAX_DIAS = 62;
    private static final int MAX_PACIENTES = 20;

    /** Grupos de estado del filtro de la agenda, con el texto que se muestra. */
    public static final Map<String, Set<EstadoCita>> GRUPOS = Map.of(
        "VIGENTES", EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA),
        "ASISTIO", EnumSet.of(EstadoCita.ASISTIO),
        "REALIZADA", EnumSet.of(EstadoCita.REALIZADA),
        "NO_ASISTIO", EnumSet.of(EstadoCita.NO_ASISTIO),
        "CANCELADAS", EnumSet.of(EstadoCita.CANCELADA_POR_PACIENTE, EstadoCita.CANCELADA_POR_CLINICA));

    private final CitaRepository citas;
    private final PacienteRepository pacientes;

    public AgendaService(CitaRepository citas, PacienteRepository pacientes) {
        this.citas = citas;
        this.pacientes = pacientes;
    }

    /**
    Búsqueda general de la agenda. Con {@code paciente} (DNI o nombre) trae las citas de los pacientes que coinciden
    en cualquier fecha, salvo que también se den fechas; sin paciente, las del rango {@code [desde, hasta]} (máximo
    {@value #MAX_DIAS} días). Un día solo se ordena por médico y hora; lo demás, por fecha y hora.

    @param estado una clave de {@link #GRUPOS}, o null para todos
    */
    public List<CitaAgenda> buscar(LocalDate desde, LocalDate hasta, Long idMedico, Short idEspecialidad,
            String estado, String paciente) {
        boolean porPaciente = paciente != null && !paciente.isBlank();
        if (!porPaciente && (desde == null || hasta == null)) {
            throw new ReglaNegocioException("Elige el día o el rango de días.");
        }
        if (desde != null && hasta != null) {
            if (hasta.isBefore(desde)) {
                throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial.");
            }
            if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
                throw new ReglaNegocioException("Elige un rango de hasta " + MAX_DIAS + " días.");
            }
        }
        Set<EstadoCita> estados = estado == null || estado.isBlank() ? null : GRUPOS.get(estado);
        Stream<Cita> base = porPaciente
            ? pacientes.buscar("%" + paciente.trim().toLowerCase() + "%", PageRequest.of(0, MAX_PACIENTES)).stream()
                .flatMap(p -> citas.delPaciente(p.getIdPaciente()).stream())
                .filter(c -> desde == null || !c.getInicio().toLocalDate().isBefore(desde))
                .filter(c -> hasta == null || !c.getInicio().toLocalDate().isAfter(hasta))
            : citas.delDia(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()).stream();
        boolean unDia = !porPaciente && desde.equals(hasta);
        Comparator<CitaAgenda> orden = unDia
            ? Comparator.comparing(CitaAgenda::medico).thenComparing(CitaAgenda::inicio)
            : Comparator.comparing(CitaAgenda::inicio).thenComparing(CitaAgenda::medico);
        return base
            .filter(c -> idMedico == null || c.getMedico().getIdMedico().equals(idMedico))
            .filter(c -> idEspecialidad == null || c.getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .filter(c -> estados == null || estados.contains(c.getEstado()))
            .map(AgendaService::fila)
            .sorted(orden)
            .toList();
    }

    /** Citas del día de todos los médicos, por médico y hora. {@code idMedico} opcional para filtrar. */
    public List<CitaAgenda> delDia(LocalDate fecha, Long idMedico) {
        return citas.delDia(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
            .filter(c -> idMedico == null || c.getMedico().getIdMedico().equals(idMedico))
            .map(AgendaService::fila)
            .sorted(Comparator.comparing(CitaAgenda::medico).thenComparing(CitaAgenda::inicio))
            .toList();
    }

    /** Una cita, con los mismos datos que una fila de la agenda. */
    @Transactional(readOnly = true)
    public CitaAgenda una(Long idCita) {
        return citas.findById(idCita).map(AgendaService::fila)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
    }

    /** Citas del día del médico de la sesión. */
    public List<CitaAgenda> delMedico(Long idMedico, LocalDate fecha) {
        return delDia(fecha, idMedico);
    }

    private static CitaAgenda fila(Cita c) {
        return new CitaAgenda(c.getIdCita(), c.getMedico().getIdMedico(),
            c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getEspecialidad().getNombre(),
            c.getInicio(), c.getFin(), c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            c.getPaciente().getTipoDocumento() + " " + c.getPaciente().getNroDocumento(),
            c.getTipoConsulta().getNombre(), c.getModalidad().name(), c.getEstado().name(),
            PacienteService.textoEstado(c.getEstado()), c.getInicioReal() != null,
            c.getTipoConsulta().isAjusteRecepcion());
    }
}
