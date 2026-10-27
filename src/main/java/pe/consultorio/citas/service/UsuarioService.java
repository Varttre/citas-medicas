package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.UsuarioFila;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidad;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.RolRepository;

/** Cuentas del personal: lista, alta de médicos y de personal, bloqueo y baja. Todo cambio queda en la auditoría. */
@Service
public class UsuarioService {

    private final CuentaRepository cuentas;
    private final RolRepository roles;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final MedicoTipoConsultaRepository medicoTipos;
    private final ConfiguracionService configuracion;
    private final CuentaService cuentaService;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    public UsuarioService(CuentaRepository cuentas, RolRepository roles, MedicoRepository medicos,
            EspecialidadRepository especialidades, MedicoEspecialidadRepository medicoEspecialidades,
            MedicoTipoConsultaRepository medicoTipos, ConfiguracionService configuracion, CuentaService cuentaService,
            AuditoriaService auditoria, PasswordEncoder encoder, Clock reloj) {
        this.cuentas = cuentas;
        this.roles = roles;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.medicoEspecialidades = medicoEspecialidades;
        this.medicoTipos = medicoTipos;
        this.configuracion = configuracion;
        this.cuentaService = cuentaService;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    /**
    Cuentas del personal (sin pacientes) filtradas por texto y rol. Las de superadministrador solo las ve otro
    superadministrador.
    */
    @Transactional(readOnly = true)
    public List<UsuarioFila> listar(String q, NombreRol rol, boolean verSuperadmin) {
        String texto = q == null ? "" : q.trim().toLowerCase();
        return cuentas.findAll().stream()
            .filter(c -> c.getRol().getNombre() != NombreRol.PACIENTE)
            .filter(c -> verSuperadmin || c.getRol().getNombre() != NombreRol.SUPERADMIN)
            .filter(c -> rol == null || c.getRol().getNombre() == rol)
            .map(this::fila)
            .filter(f -> texto.isEmpty() || f.nombre().toLowerCase().contains(texto) || f.correo().contains(texto))
            .sorted(Comparator.comparing(UsuarioFila::rol).thenComparing(UsuarioFila::nombre))
            .toList();
    }

    /** Alta de recepción o administración. */
    @Transactional
    public Long crearPersonal(String correo, String contrasena, NombreRol rol, String nombreVisible, Long idActor) {
        if (rol != NombreRol.RECEPCION && rol != NombreRol.ADMINISTRADOR) {
            throw new ReglaNegocioException("Elige recepción o administrador.");
        }
        if (nombreVisible == null || nombreVisible.isBlank()) {
            throw new ReglaNegocioException("Ingresa el nombre que se mostrará.");
        }
        Cuenta c = nuevaCuenta(correo, contrasena, rol);
        c.setNombreVisible(nombreVisible.trim());
        c = cuentas.save(c);
        auditoria.registrar(idActor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(c.getIdCuenta()),
            "alta " + rol + ": " + c.getCorreo(), null);
        return c.getIdCuenta();
    }

    /**
    Alta de médico en una sola transacción: cuenta, médico, especialidad y una duración para cada tipo de consulta
    activo (múltiplos de 15). Sin duraciones no puede recibir citas, por eso son obligatorias.
    */
    @Transactional
    public Long crearMedico(String correo, String contrasena, String nombres, String apellidos, Short idEspecialidad,
            Map<Short, Integer> duraciones, Long idActor) {
        if (nombres == null || nombres.isBlank() || apellidos == null || apellidos.isBlank()) {
            throw new ReglaNegocioException("Ingresa los nombres y apellidos del médico.");
        }
        if (idEspecialidad == null) {
            throw new ReglaNegocioException("Elige la especialidad del médico.");
        }
        List<TipoConsulta> tipos = configuracion.tiposActivos();
        for (TipoConsulta t : tipos) {
            Integer min = duraciones.get(t.getIdTipoConsulta());
            if (min == null || min <= 0 || min % 15 != 0) {
                throw new ReglaNegocioException("Falta la duración de «" + t.getNombre() + "» (múltiplo de 15).");
            }
        }
        Cuenta c = cuentas.save(nuevaCuenta(correo, contrasena, NombreRol.MEDICO));
        Medico m = new Medico();
        m.setCuenta(c);
        m.setNombres(nombres.trim());
        m.setApellidos(apellidos.trim());
        m.setActivo(true);
        m = medicos.save(m);

        MedicoEspecialidad me = new MedicoEspecialidad();
        me.setMedico(m);
        me.setEspecialidad(especialidades.findById(idEspecialidad)
            .orElseThrow(() -> new ReglaNegocioException("La especialidad no existe.")));
        medicoEspecialidades.save(me);
        for (TipoConsulta t : tipos) {
            MedicoTipoConsulta mt = new MedicoTipoConsulta();
            mt.setMedico(m);
            mt.setTipoConsulta(t);
            mt.setDuracionConfigurada(duraciones.get(t.getIdTipoConsulta()).shortValue());
            medicoTipos.save(mt);
        }
        auditoria.registrar(idActor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(c.getIdCuenta()),
            "alta MEDICO: " + c.getCorreo(), null);
        return c.getIdCuenta();
    }

    /** Bloqueo manual: la cuenta no puede entrar y sus sesiones abiertas se cierran. */
    @Transactional
    public void bloquear(Long idCuenta, String motivo, Long idActor) {
        Cuenta c = cuenta(idCuenta);
        c.setEstado(EstadoCuenta.BLOQUEADA);
        cuentaService.cerrarSesiones(c.getCorreo());
        auditoria.registrar(idActor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(idCuenta),
            "estado: ACTIVA → BLOQUEADA", motivo);
    }

    @Transactional
    public void desbloquear(Long idCuenta, Long idActor) {
        Cuenta c = cuenta(idCuenta);
        c.setEstado(EstadoCuenta.ACTIVA);
        c.setBloqueadoHasta(null);
        c.setIntentosFallidos((short) 0);
        auditoria.registrar(idActor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(idCuenta),
            "estado → ACTIVA", null);
    }

    /** Baja definitiva: no puede volver a entrar. */
    @Transactional
    public void darDeBaja(Long idCuenta, Long idActor) {
        Cuenta c = cuenta(idCuenta);
        c.setEstado(EstadoCuenta.BAJA);
        cuentaService.cerrarSesiones(c.getCorreo());
        medicos.findByCuentaIdCuenta(idCuenta).ifPresent(m -> m.setActivo(false));
        auditoria.registrar(idActor, AccionAuditoria.CONFIG_CAMBIO, "CUENTA", String.valueOf(idCuenta),
            "estado → BAJA", null);
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private Cuenta nuevaCuenta(String correo, String contrasena, NombreRol rol) {
        String normalizado = UsuarioDetallesService.normalizar(correo);
        if (normalizado.isEmpty() || !normalizado.contains("@")) {
            throw new ReglaNegocioException("Ingresa un correo válido.");
        }
        if (cuentas.findByCorreo(normalizado).isPresent()) {
            throw new ReglaNegocioException("Ya existe una cuenta con ese correo.");
        }
        if (contrasena == null || contrasena.length() < 8) {
            throw new ReglaNegocioException("La contraseña inicial debe tener al menos 8 caracteres.");
        }
        Cuenta c = new Cuenta();
        c.setCorreo(normalizado);
        c.setPasswordHash(encoder.encode(contrasena));
        c.setRol(roles.findByNombre(rol).orElseThrow());
        c.setEstado(EstadoCuenta.ACTIVA);
        c.setIntentosFallidos((short) 0);
        c.setCreadoEn(ahora());
        return c;
    }

    private Cuenta cuenta(Long idCuenta) {
        return cuentas.findById(idCuenta)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta inexistente " + idCuenta));
    }

    private UsuarioFila fila(Cuenta c) {
        NombreRol rol = c.getRol().getNombre();
        String nombre = c.getNombreVisible();
        String especialidad = "—";
        if (rol == NombreRol.MEDICO) {
            Medico m = medicos.findByCuentaIdCuenta(c.getIdCuenta()).orElse(null);
            if (m != null) {
                nombre = m.getNombres() + " " + m.getApellidos();
                especialidad = medicoEspecialidades.findAll().stream()
                    .filter(x -> x.getMedico().getIdMedico().equals(m.getIdMedico()))
                    .map(x -> x.getEspecialidad().getNombre())
                    .findFirst().orElse("—");
            }
        }
        return new UsuarioFila(c.getIdCuenta(), nombre != null ? nombre : c.getCorreo(), c.getCorreo(), textoRol(rol),
            especialidad, c.getEstado().name(), c.getBloqueadoHasta());
    }

    private static String textoRol(NombreRol rol) {
        return switch (rol) {
            case PACIENTE -> "Paciente";
            case MEDICO -> "Médico";
            case RECEPCION -> "Recepción";
            case ADMINISTRADOR -> "Administrador";
            case SUPERADMIN -> "Superadministrador";
        };
    }
}
