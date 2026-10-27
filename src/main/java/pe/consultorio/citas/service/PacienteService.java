package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.PacienteFicha;
import pe.consultorio.citas.dto.PacienteForm;
import pe.consultorio.citas.dto.PacienteResumen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.CuentaPacienteId;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.PacienteRepository;

/** Pacientes para recepción: búsqueda, ficha, registro, edición y revocación de cuentas vinculadas. */
@Service
public class PacienteService {

    private static final int MAX_RESULTADOS = 50;

    private final PacienteRepository pacientes;
    private final CuentaRepository cuentas;
    private final CuentaPacienteRepository vinculos;
    private final CitaRepository citas;
    private final Clock reloj;

    public PacienteService(PacienteRepository pacientes, CuentaRepository cuentas, CuentaPacienteRepository vinculos,
            CitaRepository citas, Clock reloj) {
        this.pacientes = pacientes;
        this.cuentas = cuentas;
        this.vinculos = vinculos;
        this.citas = citas;
        this.reloj = reloj;
    }

    /** Hasta 50 pacientes cuyo documento, nombre o teléfono contiene el texto. Vacío si no se buscó nada. */
    @Transactional(readOnly = true)
    public List<PacienteResumen> buscar(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String patron = "%" + q.trim().toLowerCase() + "%";
        return pacientes.buscar(patron, PageRequest.of(0, MAX_RESULTADOS)).stream()
            .map(p -> new PacienteResumen(p.getIdPaciente(), p.getNombres() + " " + p.getApellidos(),
                p.getTipoDocumento() + " " + p.getNroDocumento(), p.getTelefono()))
            .toList();
    }

    @Transactional(readOnly = true)
    public PacienteFicha ficha(Long idPaciente) {
        Paciente p = paciente(idPaciente);
        LocalDate hoy = LocalDate.now(reloj);
        String propia = cuentas.findByPacientePropioIdPaciente(idPaciente).map(c -> c.getCorreo()).orElse(null);
        List<PacienteFicha.CuentaVinculada> terceros = vinculos.delPaciente(idPaciente).stream()
            .filter(cp -> !cp.getCuenta().getCorreo().equals(propia))
            .map(cp -> new PacienteFicha.CuentaVinculada(cp.getCuenta().getIdCuenta(), cp.getCuenta().getCorreo(),
                cp.getConsentimientoEn(), cp.getRevocadoEn()))
            .toList();
        List<Cita> historial = citas.delPaciente(idPaciente);
        List<PacienteFicha.CitaHistorial> filas = historial.stream()
            .map(c -> new PacienteFicha.CitaHistorial(c.getIdCita(), c.getInicio(),
                c.getMedico().getNombres() + " " + c.getMedico().getApellidos(), c.getTipoConsulta().getNombre(),
                textoEstado(c.getEstado())))
            .toList();
        return new PacienteFicha(p.getIdPaciente(), p.getTipoDocumento().name(), p.getNroDocumento(), p.getNombres(),
            p.getApellidos(), p.getFechaNacimiento(), Period.between(p.getFechaNacimiento(), hoy).getYears(),
            p.getTelefono(), p.getCorreo(), propia, terceros, filas, indicadores(historial, hoy));
    }

    /** Registra un paciente nuevo. El tipo y número de documento no se pueden repetir. */
    @Transactional
    public Long registrar(PacienteForm f) {
        String nro = f.getNroDocumento().trim();
        if (pacientes.existsByTipoDocumentoAndNroDocumento(f.getTipoDocumento(), nro)) {
            throw new ReglaNegocioException("Ya existe un paciente con ese documento.");
        }
        Paciente p = new Paciente();
        copiar(f, p);
        return pacientes.save(p).getIdPaciente();
    }

    /** Edita los datos del paciente, incluido el teléfono (recepción sí puede cambiarlo). */
    @Transactional
    public void editar(Long idPaciente, PacienteForm f) {
        Paciente p = paciente(idPaciente);
        String nro = f.getNroDocumento().trim();
        boolean cambiaDocumento = p.getTipoDocumento() != f.getTipoDocumento() || !p.getNroDocumento().equals(nro);
        if (cambiaDocumento && pacientes.existsByTipoDocumentoAndNroDocumento(f.getTipoDocumento(), nro)) {
            throw new ReglaNegocioException("Ya existe un paciente con ese documento.");
        }
        copiar(f, p);
    }

    /** Formulario precargado con los datos actuales del paciente. */
    @Transactional(readOnly = true)
    public PacienteForm formulario(Long idPaciente) {
        Paciente p = paciente(idPaciente);
        PacienteForm f = new PacienteForm();
        f.setTipoDocumento(p.getTipoDocumento());
        f.setNroDocumento(p.getNroDocumento());
        f.setNombres(p.getNombres());
        f.setApellidos(p.getApellidos());
        f.setFechaNacimiento(p.getFechaNacimiento());
        f.setTelefono(p.getTelefono());
        f.setCorreo(p.getCorreo());
        return f;
    }

    /** La cuenta de un tercero deja de poder agendar por este paciente. */
    @Transactional
    public void revocarCuenta(Long idPaciente, Long idCuenta) {
        CuentaPaciente cp = vinculos.findById(new CuentaPacienteId(idCuenta, idPaciente))
            .orElseThrow(() -> new RecursoNoEncontradoException("Vínculo inexistente " + idCuenta + "/" + idPaciente));
        if (cp.getRevocadoEn() == null) {
            cp.setRevocadoEn(LocalDateTime.now(reloj));
        }
    }

    private Paciente paciente(Long idPaciente) {
        return pacientes.findById(idPaciente)
            .orElseThrow(() -> new RecursoNoEncontradoException("Paciente inexistente " + idPaciente));
    }

    private static void copiar(PacienteForm f, Paciente p) {
        p.setTipoDocumento(f.getTipoDocumento());
        p.setNroDocumento(f.getNroDocumento().trim());
        p.setNombres(f.getNombres().trim());
        p.setApellidos(f.getApellidos().trim());
        p.setFechaNacimiento(f.getFechaNacimiento());
        p.setTelefono(vacioANulo(f.getTelefono()));
        p.setCorreo(vacioANulo(f.getCorreo()));
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static PacienteFicha.Indicadores indicadores(List<Cita> historial, LocalDate hoy) {
        long realizadas = historial.stream().filter(c -> c.getEstado() == EstadoCita.REALIZADA).count();
        long noAsistio = historial.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        long canceladas = historial.stream().filter(c -> CitaRepository.CANCELADAS.contains(c.getEstado())).count();
        Integer tasaNoAsistencia = realizadas + noAsistio == 0 ? null
            : (int) Math.round(100.0 * noAsistio / (realizadas + noAsistio));
        Integer tasaCancelacion = historial.isEmpty() ? null
            : (int) Math.round(100.0 * canceladas / historial.size());
        Long dias = historial.stream()
            .map(c -> c.getInicio().toLocalDate())
            .filter(d -> !d.isAfter(hoy))
            .max(LocalDate::compareTo)
            .map(d -> ChronoUnit.DAYS.between(d, hoy))
            .orElse(null);
        return new PacienteFicha.Indicadores(tasaNoAsistencia, tasaCancelacion, dias);
    }

    /** Texto del estado para mostrar al usuario. */
    public static String textoEstado(EstadoCita e) {
        return switch (e) {
            case PROGRAMADA -> "Programada";
            case CONFIRMADA -> "Confirmada";
            case ASISTIO -> "Asistió";
            case REALIZADA -> "Realizada";
            case NO_ASISTIO -> "No asistió";
            case CANCELADA_POR_PACIENTE -> "Cancelada por el paciente";
            case CANCELADA_POR_CLINICA -> "Cancelada por el consultorio";
        };
    }
}
