package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.TableroRecepcion;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoIndicacion;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.CitaRepository;

/**
Inicio de recepción. Cada contador sale de la misma consulta que su pantalla de destino, para que coincidan: citas por
confirmar hoy (la cola de confirmaciones), cupos liberados sin cubrir, indicaciones pendientes, pacientes en sala,
citas de ayer sin estado y citas de hoy que ya empezaron sin llegada registrada.
*/
@Service
@Transactional(readOnly = true)
public class TableroRecepcionService {

    private static final Set<EstadoCita> VIGENTES = EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA);
    private static final Set<EstadoCita> CERRADAS = EnumSet.of(EstadoCita.REALIZADA, EstadoCita.NO_ASISTIO,
        EstadoCita.CANCELADA_POR_PACIENTE, EstadoCita.CANCELADA_POR_CLINICA);

    private final ColaService colas;
    private final OportunidadService oportunidades;
    private final CitaRepository citas;
    private final CitaIndicadaRepository indicadas;
    private final Clock reloj;

    public TableroRecepcionService(ColaService colas, OportunidadService oportunidades, CitaRepository citas,
            CitaIndicadaRepository indicadas, Clock reloj) {
        this.colas = colas;
        this.oportunidades = oportunidades;
        this.citas = citas;
        this.indicadas = indicadas;
        this.reloj = reloj;
    }

    public TableroRecepcion tablero() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        List<Cita> deHoy = citas.delDia(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        List<Cita> deAyer = citas.delDia(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay());

        long enSala = deHoy.stream().filter(c -> c.getEstado() == EstadoCita.ASISTIO && c.getInicioReal() == null).count();
        long ayerSinEstado = deAyer.stream().filter(c -> VIGENTES.contains(c.getEstado())).count();
        long hoySinLlegada = deHoy.stream()
            .filter(c -> VIGENTES.contains(c.getEstado()) && !c.getInicio().isAfter(ahora)).count();

        Map<Long, List<Cita>> porMedico = deHoy.stream()
            .filter(c -> !CitaRepository.CANCELADAS.contains(c.getEstado()))
            .collect(Collectors.groupingBy(c -> c.getMedico().getIdMedico()));
        List<TableroRecepcion.MedicoHoy> medicos = porMedico.values().stream()
            .map(lista -> medicoHoy(lista, ahora))
            .sorted(Comparator.comparing(TableroRecepcion.MedicoHoy::medico))
            .toList();

        return new TableroRecepcion(ahora, colas.confirmaciones(hoy, null, null).size(), oportunidades.abiertas().size(),
            indicadas.countByEstado(EstadoIndicacion.PENDIENTE), enSala, ayerSinEstado, hoySinLlegada, medicos);
    }

    private static TableroRecepcion.MedicoHoy medicoHoy(List<Cita> lista, LocalDateTime ahora) {
        Cita primera = lista.get(0);
        String nombre = primera.getMedico().getNombres() + " " + primera.getMedico().getApellidos();
        long cerradas = lista.stream().filter(c -> CERRADAS.contains(c.getEstado())).count();
        int avance = (int) Math.round(100.0 * cerradas / lista.size());
        String estado;
        String color;
        boolean atendiendo = lista.stream()
            .anyMatch(c -> c.getEstado() == EstadoCita.ASISTIO && c.getInicioReal() != null);
        long retraso = lista.stream()
            .filter(c -> c.getEstado() == EstadoCita.ASISTIO && c.getInicioReal() == null && c.getInicio().isBefore(ahora))
            .mapToLong(c -> Duration.between(c.getInicio(), ahora).toMinutes())
            .max().orElse(0);
        if (atendiendo) {
            estado = "Atendiendo";
            color = "text-bg-info";
        } else if (retraso > 0) {
            estado = "Retrasado " + retraso + " min";
            color = "text-bg-warning";
        } else if (cerradas == lista.size()) {
            estado = "Terminó por hoy";
            color = "text-bg-light border";
        } else {
            estado = "En horario";
            color = "text-bg-success";
        }
        return new TableroRecepcion.MedicoHoy(nombre, primera.getEspecialidad().getNombre(), estado, color, avance);
    }
}
