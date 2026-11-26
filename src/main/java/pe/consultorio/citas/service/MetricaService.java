package pe.consultorio.citas.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.Metricas;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoIndicacion;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaIndicadaRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.OportunidadRepository;

/**
El reporte del administrador, calculado desde el historial: resumen de citas por estado, horarios liberados
recuperados (minutos y cantidad de horarios), no asistencia por médico, especialidad y hora, citas indicadas
agendadas, reprogramaciones por cita y el detalle por médico. Se puede filtrar por médico y por especialidad.

Lo recuperado se mide contra el horario que realmente se soltó: si se cancela 08:15–08:45 y se ofrece el bloque
08:00–09:00, una cita de 08:00 a 09:00 recupera 30 minutos, no 60. Lo demás era tiempo que ya estaba libre.
*/
@Service
@Transactional(readOnly = true)
public class MetricaService {

    private final OportunidadRepository oportunidades;
    private final CitaRepository citas;
    private final CitaIndicadaRepository indicadas;
    private final CitaEventoRepository eventos;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final HorarioSoltadoService soltados;

    public MetricaService(OportunidadRepository oportunidades, CitaRepository citas, CitaIndicadaRepository indicadas,
            CitaEventoRepository eventos, MedicoRepository medicos, EspecialidadRepository especialidades,
            HorarioSoltadoService soltados) {
        this.oportunidades = oportunidades;
        this.citas = citas;
        this.indicadas = indicadas;
        this.eventos = eventos;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.soltados = soltados;
    }

    /** Todo el consultorio. */
    public Metricas calcular(LocalDate desde, LocalDate hasta) {
        return calcular(desde, hasta, null, null);
    }

    /** Con filtro opcional por médico y por especialidad (null = todos). */
    public Metricas calcular(LocalDate desde, LocalDate hasta, Long idMedico, Short idEspecialidad) {
        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        // 1. Horarios liberados: CUBIERTA / (CUBIERTA + EXPIRADA), y lo recuperado del horario soltado
        List<Oportunidad> ops = oportunidades.findAll().stream()
            .filter(o -> enRango(o.getCreadaEn(), ini, fin))
            .filter(o -> idMedico == null || o.getMedico().getIdMedico().equals(idMedico))
            .filter(o -> idEspecialidad == null
                || o.getCitaOrigen().getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .toList();
        long cubiertas = ops.stream().filter(o -> o.getEstado() == EstadoOportunidad.CUBIERTA).count();
        long expiradas = ops.stream().filter(o -> o.getEstado() == EstadoOportunidad.EXPIRADA).count();
        Recuperacion rec = recuperacion(ops);
        LocalDateTime ahora = LocalDateTime.now();
        long abiertasAhora = oportunidades.abiertas().stream()
            .filter(o -> o.getInicio().isAfter(ahora))
            .filter(o -> idMedico == null || o.getMedico().getIdMedico().equals(idMedico))
            .filter(o -> idEspecialidad == null
                || o.getCitaOrigen().getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .count();

        // 2. No asistencia: NO_ASISTIO / (REALIZADA + NO_ASISTIO), en total y por médico
        List<Cita> delRango = citas.findAll().stream()
            .filter(c -> enRango(c.getInicio(), ini, fin))
            .filter(c -> idMedico == null || c.getMedico().getIdMedico().equals(idMedico))
            .filter(c -> idEspecialidad == null || c.getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .toList();
        long realizadas = contar(delRango, EstadoCita.REALIZADA);
        long noAsistio = contar(delRango, EstadoCita.NO_ASISTIO);
        List<Metricas.NoAsistenciaMedico> porMedico = delRango.stream()
            .collect(Collectors.groupingBy(c -> c.getMedico().getIdMedico()))
            .values().stream()
            .map(lista -> {
                Cita c = lista.get(0);
                long r = contar(lista, EstadoCita.REALIZADA);
                long n = contar(lista, EstadoCita.NO_ASISTIO);
                return new Metricas.NoAsistenciaMedico(nombre(c.getMedico()), c.getEspecialidad().getNombre(), r, n,
                    pct(n, r + n));
            })
            .sorted(Comparator.comparing(Metricas.NoAsistenciaMedico::medico))
            .toList();
        List<Metricas.NoAsistenciaGrupo> porEspecialidad = grupos(delRango, c -> c.getEspecialidad().getNombre());
        List<Metricas.NoAsistenciaGrupo> porHora = grupos(delRango,
            c -> String.format("%02d:00", c.getInicio().getHour()));

        // 3. Citas indicadas que terminan agendadas: AGENDADA / (AGENDADA + VENCIDA + DESCARTADA)
        var ind = indicadas.findAll().stream()
            .filter(i -> enRango(i.getCreadoEn(), ini, fin))
            .filter(i -> idMedico == null || i.getCitaOrigen().getMedico().getIdMedico().equals(idMedico))
            .filter(i -> idEspecialidad == null
                || i.getCitaOrigen().getEspecialidad().getIdEspecialidad().equals(idEspecialidad))
            .toList();
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

        // 5. Resumen de citas y detalle por médico
        Metricas.ResumenCitas resumen = resumen(delRango, reprogramaciones);
        List<Metricas.DetalleMedico> detalle = detalle(delRango, reprogramaciones, rec);

        String filtroMedico = idMedico == null ? null : medicos.findById(idMedico).map(MetricaService::nombre)
            .orElse(null);
        String filtroEspecialidad = idEspecialidad == null ? null
            : especialidades.findById(idEspecialidad).map(e -> e.getNombre()).orElse(null);

        return new Metricas(desde, hasta, idMedico, idEspecialidad, filtroMedico, filtroEspecialidad,
            cubiertas, expiradas, pct(cubiertas, cubiertas + expiradas),
            realizadas, noAsistio, pct(noAsistio, realizadas + noAsistio), porMedico,
            agendadas, cerradas, pct(agendadas, cerradas),
            delRango.size(), promedio, max, distribucion, rec.minutos(), abiertasAhora, porEspecialidad, porHora,
            rec.horarios(), rec.soltados().size(), rec.minutosSoltados(), resumen, detalle);
    }

    /**
    Lo recuperado de los horarios soltados. Para cada bloque cubierto se cuenta solo el cruce entre la cita que lo
    ocupó, el bloque y cada horario soltado que contiene. Un horario cubierto en partes (por dos citas) cuenta una vez.
    */
    private Recuperacion recuperacion(List<Oportunidad> ops) {
        Map<Long, HorarioSoltadoService.Soltado> todos = new HashMap<>();
        Map<Long, Long> minutosPorHorario = new HashMap<>();
        Map<Long, Long> medicoPorHorario = new HashMap<>();
        for (Oportunidad o : ops) {
            for (HorarioSoltadoService.Soltado s : soltados.todos(o)) {
                todos.putIfAbsent(s.idRaiz(), s);
                medicoPorHorario.putIfAbsent(s.idRaiz(), o.getMedico().getIdMedico());
                Cita c = o.getCitaCobertura();
                if (o.getEstado() == EstadoOportunidad.CUBIERTA && c != null) {
                    long m = cruce(c.getInicio(), c.getFin(), o.getInicio(), o.getFin(), s.inicio(), s.fin());
                    minutosPorHorario.merge(s.idRaiz(), m, Long::sum);
                }
            }
        }
        Map<Long, long[]> porMedico = new HashMap<>();
        minutosPorHorario.forEach((idRaiz, min) -> {
            if (min > 0) {
                long[] acum = porMedico.computeIfAbsent(medicoPorHorario.get(idRaiz), k -> new long[2]);
                acum[0] += min;
                acum[1]++;
            }
        });
        long minutos = minutosPorHorario.values().stream().mapToLong(Long::longValue).sum();
        long horarios = minutosPorHorario.values().stream().filter(m -> m > 0).count();
        long minutosSoltados = todos.values().stream()
            .mapToLong(s -> Math.max(0, Duration.between(s.inicio(), s.fin()).toMinutes())).sum();
        return new Recuperacion(minutos, horarios, new HashSet<>(todos.keySet()), minutosSoltados, porMedico);
    }

    private record Recuperacion(long minutos, long horarios, Set<Long> soltados, long minutosSoltados,
            Map<Long, long[]> porMedico) { }

    /** Minutos en que se cruzan los tres tramos {@code [ini, fin)}. */
    private static long cruce(LocalDateTime i1, LocalDateTime f1, LocalDateTime i2, LocalDateTime f2,
            LocalDateTime i3, LocalDateTime f3) {
        LocalDateTime desde = max(max(i1, i2), i3);
        LocalDateTime hasta = min(min(f1, f2), f3);
        return Math.max(0, Duration.between(desde, hasta).toMinutes());
    }

    private static Metricas.ResumenCitas resumen(List<Cita> lista, Map<Long, Long> reprogramaciones) {
        return new Metricas.ResumenCitas(lista.size(),
            contar(lista, EstadoCita.PROGRAMADA) + contar(lista, EstadoCita.CONFIRMADA),
            contar(lista, EstadoCita.ASISTIO), contar(lista, EstadoCita.REALIZADA),
            contar(lista, EstadoCita.NO_ASISTIO), contar(lista, EstadoCita.CANCELADA_POR_PACIENTE),
            contar(lista, EstadoCita.CANCELADA_POR_CLINICA),
            lista.stream().filter(c -> reprogramaciones.getOrDefault(c.getIdCita(), 0L) > 0).count());
    }

    /** Una fila por médico con citas en el rango o con horarios recuperados, en orden alfabético. */
    private List<Metricas.DetalleMedico> detalle(List<Cita> delRango, Map<Long, Long> reprogramaciones,
            Recuperacion rec) {
        Map<Long, List<Cita>> porMedico = delRango.stream()
            .collect(Collectors.groupingBy(c -> c.getMedico().getIdMedico()));
        Set<Long> ids = new HashSet<>(porMedico.keySet());
        ids.addAll(rec.porMedico().keySet());
        return ids.stream()
            .map(id -> {
                List<Cita> lista = porMedico.getOrDefault(id, List.of());
                Medico m = lista.isEmpty() ? medicos.findById(id).orElseThrow() : lista.get(0).getMedico();
                String esp = lista.stream().map(c -> c.getEspecialidad().getNombre())
                    .collect(Collectors.toCollection(TreeSet::new)).stream().collect(Collectors.joining(", "));
                long[] r = rec.porMedico().getOrDefault(id, new long[2]);
                return new Metricas.DetalleMedico(nombre(m), esp, resumen(lista, reprogramaciones), r[0], r[1]);
            })
            .sorted(Comparator.comparing(Metricas.DetalleMedico::medico))
            .toList();
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

    private static String nombre(Medico m) {
        return m.getNombres() + " " + m.getApellidos();
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
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
