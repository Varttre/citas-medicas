package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.EstadisticasMedico;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;

/**
«Mis estadísticas» del médico: duración real por tipo de consulta (mediana y P75 de sus últimas citas realizadas),
puntualidad, no asistencia y minutos perdidos de los últimos 30 días. Nunca usa datos de otros médicos.
*/
@Service
@Transactional(readOnly = true)
public class EstadisticaMedicoService {

    private static final int DIAS = 30;

    private final CitaRepository citas;
    private final MedicoTipoConsultaRepository duraciones;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public EstadisticaMedicoService(CitaRepository citas, MedicoTipoConsultaRepository duraciones,
            ConfiguracionService configuracion, Clock reloj) {
        this.citas = citas;
        this.duraciones = duraciones;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    public EstadisticasMedico calcular(Long idMedico) {
        int ventana = configuracion.parametros().getNVentanaEstimacion();
        int paso = configuracion.incremento();
        List<Cita> realizadas = citas.realizadasDelMedico(idMedico);
        List<EstadisticasMedico.DuracionTipo> porTipo = duraciones.delMedico(idMedico).stream()
            .filter(mt -> mt.getTipoConsulta().isActivo())
            .map(mt -> {
                List<Integer> minutos = realizadas.stream()
                    .filter(c -> c.getTipoConsulta().getIdTipoConsulta().equals(mt.getTipoConsulta().getIdTipoConsulta()))
                    .limit(ventana)
                    .map(c -> (int) Duration.between(c.getInicioReal(), c.getFinReal()).toMinutes())
                    .toList();
                Double mediana = Estadistica.mediana(minutos);
                Integer p75 = Estadistica.percentil(minutos, 75);
                boolean variable = minutos.size() >= 2
                    && Estadistica.ranura(mediana, paso) != Estadistica.ranura(p75, paso);
                return new EstadisticasMedico.DuracionTipo(mt.getTipoConsulta().getNombre(),
                    mt.getDuracionConfigurada(), mediana, p75, minutos.size(), variable);
            })
            .toList();

        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Cita> recientes = citas.delMedicoEntre(idMedico, ahora.minusDays(DIAS), ahora);
        List<Long> retrasos = recientes.stream()
            .filter(c -> c.getEstado() == EstadoCita.REALIZADA && c.getInicioReal() != null)
            .map(c -> Duration.between(c.getInicio(), c.getInicioReal()).toMinutes())
            .toList();
        Double retrasoPromedio = retrasos.isEmpty() ? null
            : retrasos.stream().mapToLong(Long::longValue).average().orElse(0);
        Integer tardePct = retrasos.isEmpty() ? null
            : (int) Math.round(100.0 * retrasos.stream().filter(r -> r > 0).count() / retrasos.size());
        long realizadas30 = recientes.stream().filter(c -> c.getEstado() == EstadoCita.REALIZADA).count();
        long noAsistio30 = recientes.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        Integer noAsistenciaPct = realizadas30 + noAsistio30 == 0 ? null
            : (int) Math.round(100.0 * noAsistio30 / (realizadas30 + noAsistio30));
        long perdidos = recientes.stream()
            .filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO || CitaRepository.CANCELADAS.contains(c.getEstado()))
            .mapToLong(c -> Duration.between(c.getInicio(), c.getFin()).toMinutes())
            .sum();
        return new EstadisticasMedico(ventana, porTipo, retrasoPromedio, tardePct, noAsistenciaPct, perdidos);
    }
}
