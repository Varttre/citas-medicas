package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaAgenda;
import pe.consultorio.citas.dto.TableroMedico;
import pe.consultorio.citas.repository.AuditoriaRepository;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.CitaRepository;

/**
Inicio del médico: todo se calcula solo con sus citas (las mismas que ve en «Mi agenda»), nunca con las de otros
médicos.
*/
@Service
@Transactional(readOnly = true)
public class TableroMedicoService {

    private static final Set<String> PENDIENTES = Set.of("PROGRAMADA", "CONFIRMADA", "ASISTIO");
    private static final Set<String> CANCELADAS = Set.of("CANCELADA_POR_PACIENTE", "CANCELADA_POR_CLINICA");

    private final AgendaService agenda;
    private final CitaIndicadaRepository indicadas;
    private final AuditoriaRepository auditorias;
    private final CitaRepository citas;
    private final Clock reloj;

    public TableroMedicoService(AgendaService agenda, CitaIndicadaRepository indicadas, AuditoriaRepository auditorias,
            CitaRepository citas, Clock reloj) {
        this.agenda = agenda;
        this.indicadas = indicadas;
        this.auditorias = auditorias;
        this.citas = citas;
        this.reloj = reloj;
    }

    public TableroMedico tablero(Long idMedico, Long idCuenta) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        List<CitaAgenda> deHoy = agenda.delMedico(idMedico, hoy).stream()
            .filter(c -> !CANCELADAS.contains(c.estadoCodigo())).toList();

        CitaAgenda proxima = deHoy.stream()
            .filter(c -> c.vigente() || c.asistio() && !c.atencionIniciada())
            .filter(c -> c.fin().isAfter(ahora))
            .min(Comparator.comparing(CitaAgenda::inicio))
            .orElse(null);

        Long retraso = citas.delDia(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()).stream()
            .filter(c -> c.getMedico().getIdMedico().equals(idMedico) && c.getInicioReal() != null)
            .max(Comparator.comparing(c -> c.getInicioReal()))
            .map(c -> Duration.between(c.getInicio(), c.getInicioReal()).toMinutes())
            .orElse(null);

        List<TableroMedico.IndicacionPendiente> pendientesIndicadas = indicadas.pendientesDeMedico(idMedico).stream()
            .map(i -> new TableroMedico.IndicacionPendiente(
                i.getCitaOrigen().getPaciente().getNombres() + " " + i.getCitaOrigen().getPaciente().getApellidos(),
                i.getTipoConsulta().getNombre(), i.getVigenteHasta()))
            .toList();
        List<String> rechazados = idCuenta == null ? List.of() : auditorias
            .findByCuentaIdCuentaAndAccionAndFechaHoraGreaterThanEqualOrderByFechaHoraDesc(idCuenta,
                AccionAuditoria.CAMBIO_HORARIO_RECHAZADO, ahora.minusDays(7))
            .stream().map(a -> a.getDetalle()).toList();

        return new TableroMedico(deHoy.size(),
            deHoy.stream().filter(c -> "CONFIRMADA".equals(c.estadoCodigo())).count(),
            deHoy.stream().filter(c -> "REALIZADA".equals(c.estadoCodigo())).count(),
            deHoy.stream().filter(c -> PENDIENTES.contains(c.estadoCodigo())).count(),
            proxima, retraso, pendientesIndicadas, rechazados);
    }
}
