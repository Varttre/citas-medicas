package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.CitaAfectada;
import pe.consultorio.citas.dto.DuracionDatos;
import pe.consultorio.citas.dto.ExcepcionDatos;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.dto.HorarioMedicoDatos;
import pe.consultorio.citas.dto.VersionHorarioDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.ExcepcionHorario;
import pe.consultorio.citas.entity.FranjaHoraria;
import pe.consultorio.citas.entity.HorarioVersion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.MedicoTipoConsultaId;
import pe.consultorio.citas.exception.HorarioConCitasException;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.ExcepcionHorarioRepository;
import pe.consultorio.citas.repository.HorarioVersionRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.ParametroConsultorioRepository;

/**
El horario de atención de un médico: versiones con vigencia, ausencias puntuales y duración por tipo de consulta.

Regla principal: <b>ningún cambio puede dejar fuera una cita ya agendada</b>. Si lo haría, se rechaza, se deja
constancia en la auditoría y se devuelven las citas que lo impiden para que se reprogramen primero.

Todos los métodos reciben el {@code idMedico} de la sesión, nunca uno tomado de la URL.
*/
@Service
public class HorarioService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");
    private static final short FILA_PARAMETROS = 1;
    private static final int INCREMENTO_POR_DEFECTO = 15;

    private final MedicoRepository medicos;
    private final HorarioVersionRepository versiones;
    private final ExcepcionHorarioRepository excepciones;
    private final MedicoTipoConsultaRepository duraciones;
    private final CitaRepository citas;
    private final ParametroConsultorioRepository parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public HorarioService(MedicoRepository medicos, HorarioVersionRepository versiones,
            ExcepcionHorarioRepository excepciones, MedicoTipoConsultaRepository duraciones, CitaRepository citas,
            ParametroConsultorioRepository parametros, AuditoriaService auditoria, Clock reloj) {
        this.medicos = medicos;
        this.versiones = versiones;
        this.excepciones = excepciones;
        this.duraciones = duraciones;
        this.citas = citas;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------- Consulta

    /** Versiones, ausencias que aún no terminaron y duraciones del médico. */
    @Transactional(readOnly = true)
    public HorarioMedicoDatos verHorario(Long idMedico) {
        medico(idMedico);
        LocalDate hoy = hoy();
        List<VersionHorarioDatos> listaVersiones = versiones.findByMedicoIdMedicoOrderByVigenteDesdeDesc(idMedico)
            .stream()
            .map(v -> new VersionHorarioDatos(v.getIdHorarioVersion(), v.getVigenteDesde(), v.getVigenteHasta(),
                estado(v, hoy), franjasOrdenadas(v)))
            .toList();
        List<ExcepcionDatos> listaExcepciones = excepciones
            .findByMedicoIdMedicoAndFechaFinGreaterThanEqualOrderByFechaInicio(idMedico, hoy)
            .stream()
            .map(e -> new ExcepcionDatos(e.getIdExcepcion(), e.getFechaInicio(), e.getFechaFin(), e.getHoraInicio(),
                e.getHoraFin(), e.getMotivo()))
            .toList();
        List<DuracionDatos> listaDuraciones = duraciones.delMedico(idMedico).stream()
            .map(d -> new DuracionDatos(d.getTipoConsulta().getIdTipoConsulta(), d.getTipoConsulta().getNombre(),
                d.getTipoConsulta().getDuracionDefecto(), d.getDuracionConfigurada()))
            .toList();
        return new HorarioMedicoDatos(listaVersiones, listaExcepciones, listaDuraciones);
    }

    // ---------------------------------------------------------------- Versiones

    /**
    Programa un horario nuevo desde {@code desde}. La versión vigente se cierra el día anterior y la nueva queda sin
    fecha de fin.
    
    @throws HorarioConCitasException si alguna cita desde esa fecha no cabe entera en el horario nuevo
    */
    @Transactional
    public void crearVersion(Long idMedico, LocalDate desde, List<FranjaDatos> franjas, Long idCuentaActor) {
        Medico medico = medico(idMedico);
        if (desde == null || !desde.isAfter(hoy())) {
            throw new ReglaNegocioException("La nueva versión debe empezar después de hoy.");
        }
        List<FranjaDatos> nuevas = franjas == null ? List.of() : franjas;
        validarFranjas(nuevas, incremento());

        List<HorarioVersion> existentes = versiones.findByMedicoIdMedicoOrderByVigenteDesdeDesc(idMedico);
        existentes.stream()
            .filter(v -> !v.getVigenteDesde().isBefore(desde))
            .min(Comparator.comparing(HorarioVersion::getVigenteDesde))
            .ifPresent(v -> {
                throw new ReglaNegocioException("Ya hay una versión programada desde el "
                    + v.getVigenteDesde().format(DIA_MES) + ". Elimínala o cámbiala.");
            });

        List<Cita> fuera = citas.delMedicoDesde(idMedico, desde.atStartOfDay(), CitaRepository.CANCELADAS).stream()
            .filter(c -> !cabeEnAlgunaFranja(c, nuevas))
            .toList();
        if (!fuera.isEmpty()) {
            auditoria.registrar(idCuentaActor, AccionAuditoria.CAMBIO_HORARIO_RECHAZADO, "HORARIO_VERSION",
                String.valueOf(idMedico), "Nueva versión desde " + desde.format(FECHA) + ": " + fuera.size()
                    + " citas quedarían fuera del horario", null);
            throw new HorarioConCitasException("Este cambio deja fuera " + citasAgendadas(fuera.size())
                + ". Reprográmalas primero.", afectadas(fuera));
        }

        // La base no permite dos versiones con vigencias cruzadas: la anterior se cierra y se escribe ya (flush),
        // antes de insertar la nueva. Si no, Hibernate insertaría primero y PostgreSQL rechazaría el cruce.
        for (HorarioVersion v : existentes) {
            boolean cubreDesde = v.getVigenteDesde().isBefore(desde)
                && (v.getVigenteHasta() == null || !v.getVigenteHasta().isBefore(desde));
            if (cubreDesde) {
                v.setVigenteHasta(desde.minusDays(1));
                versiones.saveAndFlush(v);
            }
        }

        HorarioVersion nueva = new HorarioVersion();
        nueva.setMedico(medico);
        nueva.setVigenteDesde(desde);
        for (FranjaDatos f : nuevas) {
            FranjaHoraria franja = new FranjaHoraria();
            franja.setDiaSemana((short) f.diaSemana());
            franja.setHoraInicio(f.inicio());
            franja.setHoraFin(f.fin());
            nueva.agregarFranja(franja);
        }
        versiones.save(nueva);
    }

    // ---------------------------------------------------------------- Excepciones

    /**
    Registra una ausencia de {@code fechaInicio} a {@code fechaFin}. Sin horas, cubre los días completos; con horas,
    solo ese rango en cada día.
    
    @throws HorarioConCitasException si la ausencia cubre citas ya agendadas
    */
    @Transactional
    public void crearExcepcion(Long idMedico, LocalDate fechaInicio, LocalDate fechaFin, LocalTime horaInicio,
            LocalTime horaFin, String motivo, Long idCuentaActor) {
        Medico medico = medico(idMedico);
        if (fechaInicio == null || fechaFin == null) {
            throw new ReglaNegocioException("Indica las fechas de la excepción.");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial.");
        }
        if (fechaInicio.isBefore(hoy())) {
            throw new ReglaNegocioException("La excepción no puede empezar en una fecha pasada.");
        }
        boolean diaCompleto = horaInicio == null && horaFin == null;
        if (!diaCompleto) {
            if (horaInicio == null || horaFin == null) {
                throw new ReglaNegocioException("Indica la hora de inicio y la de fin, o marca día completo.");
            }
            validarRango(horaInicio, horaFin, incremento(), "la excepción");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("Indica el motivo de la excepción.");
        }
        if (motivo.length() > 255) {
            throw new ReglaNegocioException("El motivo no puede pasar de 255 caracteres.");
        }

        List<Cita> cubiertas = citas.delMedicoQueCruzan(idMedico, fechaInicio.atStartOfDay(),
                fechaFin.plusDays(1).atStartOfDay(), CitaRepository.CANCELADAS).stream()
            .filter(c -> diaCompleto
                || (c.getInicio().toLocalTime().isBefore(horaFin) && c.getFin().toLocalTime().isAfter(horaInicio)))
            .toList();
        if (!cubiertas.isEmpty()) {
            auditoria.registrar(idCuentaActor, AccionAuditoria.CAMBIO_HORARIO_RECHAZADO, "EXCEPCION_HORARIO",
                String.valueOf(idMedico), "Excepción del " + fechaInicio.format(FECHA) + " al "
                    + fechaFin.format(FECHA) + ": cubre " + cubiertas.size() + " citas", motivo);
            throw new HorarioConCitasException("Esta excepción cubre " + citasAgendadas(cubiertas.size())
                + ". Reprográmalas primero.", afectadas(cubiertas));
        }

        ExcepcionHorario e = new ExcepcionHorario();
        e.setMedico(medico);
        e.setFechaInicio(fechaInicio);
        e.setFechaFin(fechaFin);
        e.setHoraInicio(horaInicio);
        e.setHoraFin(horaFin);
        e.setMotivo(motivo.trim());
        excepciones.save(e);
    }

    /** Quita una ausencia que todavía no empieza. Una ausencia de otro médico se trata como inexistente. */
    @Transactional
    public void eliminarExcepcion(Long idMedico, Long idExcepcion) {
        ExcepcionHorario e = excepciones.findById(idExcepcion)
            .filter(x -> x.getMedico().getIdMedico().equals(idMedico))
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "Excepción " + idExcepcion + " inexistente o de otro médico"));
        if (!e.getFechaInicio().isAfter(hoy())) {
            throw new ReglaNegocioException("Solo se pueden quitar excepciones que aún no empiezan.");
        }
        excepciones.delete(e);
    }

    // ---------------------------------------------------------------- Duraciones

    /** Cambia la duración que usa el médico para un tipo de consulta. */
    @Transactional
    public void cambiarDuracion(Long idMedico, Short idTipoConsulta, int minutos, Long idCuentaActor) {
        MedicoTipoConsulta d = duraciones.findById(new MedicoTipoConsultaId(idMedico, idTipoConsulta))
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "El médico " + idMedico + " no tiene el tipo " + idTipoConsulta));
        int incremento = incremento();
        if (minutos <= 0 || minutos % incremento != 0) {
            throw new ReglaNegocioException("La duración debe ser un múltiplo de " + incremento + " minutos.");
        }
        int anterior = d.getDuracionConfigurada();
        if (anterior == minutos) {
            return;
        }
        d.setDuracionConfigurada((short) minutos);
        auditoria.registrar(idCuentaActor, AccionAuditoria.CONFIG_CAMBIO, "MEDICO_TIPO_CONSULTA",
            idMedico + ":" + idTipoConsulta, d.getTipoConsulta().getNombre() + ": " + anterior + " → " + minutos
                + " min", null);
    }

    // ---------------------------------------------------------------- Apoyo

    private Medico medico(Long idMedico) {
        return medicos.findById(idMedico)
            .orElseThrow(() -> new RecursoNoEncontradoException("Médico " + idMedico + " inexistente"));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** Incremento del consultorio. Mientras no exista la fila de parámetros (base sin datos iniciales), 15 minutos. */
    private int incremento() {
        return parametros.findById(FILA_PARAMETROS)
            .map(p -> (int) p.getIncrementoMin())
            .orElse(INCREMENTO_POR_DEFECTO);
    }

    private static String estado(HorarioVersion v, LocalDate hoy) {
        if (v.getVigenteDesde().isAfter(hoy)) {
            return VersionHorarioDatos.PROGRAMADA;
        }
        if (v.getVigenteHasta() != null && v.getVigenteHasta().isBefore(hoy)) {
            return VersionHorarioDatos.ANTERIOR;
        }
        return VersionHorarioDatos.VIGENTE;
    }

    private static List<FranjaDatos> franjasOrdenadas(HorarioVersion v) {
        return v.getFranjas().stream()
            .map(f -> new FranjaDatos(f.getDiaSemana(), f.getHoraInicio(), f.getHoraFin()))
            .sorted(Comparator.comparingInt(FranjaDatos::diaSemana).thenComparing(FranjaDatos::inicio))
            .toList();
    }

    /** Cada franja en la rejilla, con fin posterior al inicio, y sin cruces entre franjas del mismo día. */
    private static void validarFranjas(List<FranjaDatos> franjas, int incremento) {
        for (FranjaDatos f : franjas) {
            if (f.diaSemana() < 1 || f.diaSemana() > 7) {
                throw new ReglaNegocioException("Día de la semana no válido.");
            }
            if (f.inicio() == null || f.fin() == null) {
                throw new ReglaNegocioException("Cada franja necesita hora de inicio y de fin.");
            }
            validarRango(f.inicio(), f.fin(), incremento, "la franja del " + nombreDia(f.diaSemana()));
        }
        List<FranjaDatos> ordenadas = franjas.stream()
            .sorted(Comparator.comparingInt(FranjaDatos::diaSemana).thenComparing(FranjaDatos::inicio))
            .toList();
        for (int i = 1; i < ordenadas.size(); i++) {
            FranjaDatos anterior = ordenadas.get(i - 1);
            FranjaDatos actual = ordenadas.get(i);
            if (anterior.diaSemana() == actual.diaSemana() && actual.inicio().isBefore(anterior.fin())) {
                throw new ReglaNegocioException("Las franjas del " + nombreDia(actual.diaSemana()) + " se cruzan.");
            }
        }
    }

    private static void validarRango(LocalTime inicio, LocalTime fin, int incremento, String que) {
        if (!fin.isAfter(inicio)) {
            throw new ReglaNegocioException("En " + que + ", la hora de fin debe ser posterior a la de inicio.");
        }
        if (!enRejilla(inicio, incremento) || !enRejilla(fin, incremento)) {
            throw new ReglaNegocioException("En " + que + ", las horas deben ir en múltiplos de " + incremento
                + " minutos.");
        }
    }

    private static boolean enRejilla(LocalTime hora, int incremento) {
        return hora.getSecond() == 0 && hora.getNano() == 0
            && (hora.getHour() * 60 + hora.getMinute()) % incremento == 0;
    }

    /** La cita cabe entera en una franja de su mismo día de la semana. */
    private static boolean cabeEnAlgunaFranja(Cita c, List<FranjaDatos> franjas) {
        if (!c.getFin().toLocalDate().equals(c.getInicio().toLocalDate())) {
            return false;
        }
        int dia = c.getInicio().getDayOfWeek().getValue();
        LocalTime inicio = c.getInicio().toLocalTime();
        LocalTime fin = c.getFin().toLocalTime();
        return franjas.stream().anyMatch(f -> f.diaSemana() == dia
            && !inicio.isBefore(f.inicio()) && !fin.isAfter(f.fin()));
    }

    private static List<CitaAfectada> afectadas(List<Cita> lista) {
        return lista.stream()
            .map(c -> new CitaAfectada(c.getIdCita(), c.getInicio(), c.getFin(),
                c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos()))
            .toList();
    }

    private static String citasAgendadas(int n) {
        return n == 1 ? "1 cita agendada" : n + " citas agendadas";
    }

    private static String nombreDia(int diaSemana) {
        return DayOfWeek.of(diaSemana).getDisplayName(TextStyle.FULL, ESPANOL);
    }
}
