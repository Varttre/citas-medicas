package pe.consultorio.citas.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.Metricas;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoIndicacion;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;

/**
Las cuatro métricas del tablero del administrador, calculadas desde el historial: horarios liberados recuperados
(con los minutos recuperados), no asistencia por médico, especialidad y hora, citas indicadas agendadas y
reprogramaciones por cita.
*/
@Service
@Transactional(readOnly = true)
public class MetricaService {

    private final OportunidadRepository oportunidades;
    private final CitaRepository citas;
    private final CitaIndicadaRepository indicadas;
    private final CitaEventoRepository eventos;

    public MetricaService(OportunidadRepository oportunidades, CitaRepository citas, CitaIndicadaRepository indicadas,
            CitaEventoRepository eventos) {
        this.oportunidades = oportunidades;
        this.citas = citas;
        this.indicadas = indicadas;
        this.eventos = eventos;
    }

    public Metricas calcular(LocalDate desde, LocalDate hasta) {
        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        // 1. Horarios liberados que se recuperaron: CUBIERTA / (CUBIERTA + EXPIRADA)
        var ops = oportunidades.findAll().stream().filter(o -> enRango(o.getCreadaEn(), ini, fin)).toList();
        long cubiertas = ops.stream().filter(o -> o.getEstado() == EstadoOportunidad.CUBIERTA).count();
        long expiradas = ops.stream().filter(o -> o.getEstado() == EstadoOportunidad.EXPIRADA).count();
        long minutosRecuperados = ops.stream()
            .filter(o -> o.getEstado() == EstadoOportunidad.CUBIERTA && o.getCitaCobertura() != null)
            .mapToLong(o -> {
                Cita c = o.getCitaCobertura();
                LocalDateTime desdeCubierto = c.getInicio().isAfter(o.getInicio()) ? c.getInicio() : o.getInicio();
                LocalDateTime hastaCubierto = c.getFin().isBefore(o.getFin()) ? c.getFin() : o.getFin();
                return Math.max(0, Duration.between(desdeCubierto, hastaCubierto).toMinutes());
            })
            .sum();
        long abiertasAhora = oportunidades.abiertas().size();

        // 2. No asistencia: NO_ASISTIO / (REALIZADA + NO_ASISTIO), en total y por médico
        List<Cita> delRango = citas.findAll().stream().filter(c -> enRango(c.getInicio(), ini, fin)).toList();
        long realizadas = contar(delRango, EstadoCita.REALIZADA);
        long noAsistio = contar(delRango, EstadoCita.NO_ASISTIO);
        List<Metricas.NoAsistenciaMedico> porMedico = delRango.stream()
            .collect(Collectors.groupingBy(c -> c.getMedico().getIdMedico()))
            .values().stream()
            .map(lista -> {
                Cita c = lista.get(0);
                long r = contar(lista, EstadoCita.REALIZADA);
                long n = contar(lista, EstadoCita.NO_ASISTIO);
                return new Metricas.NoAsistenciaMedico(c.getMedico().getNombres() + " " + c.getMedico().getApellidos(),
                    c.getEspecialidad().getNombre(), r, n, pct(n, r + n));
            })
            .sorted(Comparator.comparing(Metricas.NoAsistenciaMedico::medico))
            .toList();
        List<Metricas.NoAsistenciaGrupo> porEspecialidad = grupos(delRango, c -> c.getEspecialidad().getNombre());
        List<Metricas.NoAsistenciaGrupo> porHora = grupos(delRango,
            c -> String.format("%02d:00", c.getInicio().getHour()));

        // 3. Citas indicadas que terminan agendadas: AGENDADA / (AGENDADA + VENCIDA + DESCARTADA)
        var ind = indicadas.findAll().stream().filter(i -> enRango(i.getCreadoEn(), ini, fin)).toList();
        long agendadas = ind.stream().filter(i -> i.getEstado() == EstadoIndicacion.AGENDADA).count();
        long cerradas = ind.stream().filter(i -> i.getEstado() != EstadoIndicacion.PENDIENTE).count();

        // 4. Reprogramaciones por cita (de las citas del rango)
        Map<Long, Long> reprogramaciones = eventos.findAll().stream()
            .filter(e -> e.getTipoEvento() == TipoEvento.REPROGRAMACION)
            .collect(Collectors.groupingBy(e -> e.getCita().getIdCita(), Collectors.counting()));
        long[] distribucion = new long[4];
        long total = 0;
        long max = 0;
        for (Cita c : delRango) {
            long n = reprogramaciones.getOrDefault(c.getIdCita(), 0L);
            distribucion[(int) Math.min(n, 3)]++;
            total += n;
            max = Math.max(max, n);
        }
        double promedio = delRango.isEmpty() ? 0 : (double) total / delRango.size();

        return new Metricas(desde, hasta, cubiertas, expiradas, pct(cubiertas, cubiertas + expiradas),
            realizadas, noAsistio, pct(noAsistio, realizadas + noAsistio), porMedico,
            agendadas, cerradas, pct(agendadas, cerradas),
            delRango.size(), promedio, max, distribucion, minutosRecuperados, abiertasAhora, porEspecialidad, porHora);
    }

    /** No asistencia agrupada (por especialidad u hora), en orden alfabético del grupo; solo grupos con casos. */
    private static List<Metricas.NoAsistenciaGrupo> grupos(List<Cita> lista, Function<Cita, String> clave) {
        return lista.stream()
            .collect(Collectors.groupingBy(clave, TreeMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(e -> {
                long r = contar(e.getValue(), EstadoCita.REALIZADA);
                long n = contar(e.getValue(), EstadoCita.NO_ASISTIO);
                return new Metricas.NoAsistenciaGrupo(e.getKey(), r, n, pct(n, r + n));
            })
            .filter(g -> g.realizadas() + g.noAsistio() > 0)
            .toList();
    }

    private static boolean enRango(LocalDateTime t, LocalDateTime ini, LocalDateTime fin) {
        return t != null && !t.isBefore(ini) && t.isBefore(fin);
    }

    private static long contar(List<Cita> lista, EstadoCita estado) {
        return lista.stream().filter(c -> c.getEstado() == estado).count();
    }

    private static Integer pct(long parte, long total) {
        return total == 0 ? null : (int) Math.round(100.0 * parte / total);
    }
}
