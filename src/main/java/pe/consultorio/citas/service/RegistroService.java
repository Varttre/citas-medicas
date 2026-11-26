package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.ParaQuien;
import pe.consultorio.citas.dto.RegistroForm;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.dto.TerceroVista;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.RolRepository;

/**
Registro de una cuenta de paciente. Registrarse crea el paciente propio; si recepción ya lo había registrado, la
cuenta se vincula solo si coinciden la fecha de nacimiento y el teléfono.
*/
@Service
public class RegistroService {

    static final String CORREO_USADO = "Ese correo ya tiene una cuenta. ¿Olvidaste tu contraseña?";
    static final String YA_TIENE_CUENTA = "Ya existe una cuenta con estos datos. Recupera tu contraseña.";
    static final String NO_COINCIDE = "No pudimos validar tus datos. Comunícate con el consultorio.";

    private final CuentaRepository cuentas;
    private final PacienteRepository pacientes;
    private final CuentaPacienteRepository vinculos;
    private final RolRepository roles;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    public RegistroService(CuentaRepository cuentas, PacienteRepository pacientes, CuentaPacienteRepository vinculos,
            RolRepository roles, AuditoriaService auditoria, PasswordEncoder encoder, Clock reloj) {
        this.cuentas = cuentas;
        this.pacientes = pacientes;
        this.vinculos = vinculos;
        this.roles = roles;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    @Transactional
    public Long registrar(RegistroForm f) {
        String correo = UsuarioDetallesService.normalizar(f.getCorreo());
        if (cuentas.findByCorreo(correo).isPresent()) {
            throw new ReglaNegocioException(CORREO_USADO);
        }
        String nro = f.getNroDocumento().trim();
        Paciente existente = pacientes.findAll().stream()
            .filter(p -> p.getTipoDocumento() == f.getTipoDocumento() && p.getNroDocumento().equals(nro))
            .findFirst().orElse(null);

        Paciente propio;
        boolean vinculado = false;
        if (existente == null) {
            propio = new Paciente();
            propio.setTipoDocumento(f.getTipoDocumento());
            propio.setNroDocumento(nro);
            propio.setNombres(f.getNombres().trim());
            propio.setApellidos(f.getApellidos().trim());
            propio.setFechaNacimiento(f.getFechaNacimiento());
            propio.setTelefono(f.getTelefono().trim());
            propio.setCorreo(correo);
            propio = pacientes.save(propio);
        } else if (cuentas.findByPacientePropioIdPaciente(existente.getIdPaciente()).isPresent()) {
            throw new ReglaNegocioException(YA_TIENE_CUENTA);
        } else if (existente.getFechaNacimiento().equals(f.getFechaNacimiento())
                && digitos(existente.getTelefono()).equals(digitos(f.getTelefono()))) {
            propio = existente;
            vinculado = true;
        } else {
            throw new ReglaNegocioException(NO_COINCIDE);
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        Cuenta c = new Cuenta();
        c.setCorreo(correo);
        c.setPasswordHash(encoder.encode(f.getContrasena()));
        c.setRol(roles.findByNombre(NombreRol.PACIENTE).orElseThrow());
        c.setPacientePropio(propio);
        c.setEstado(EstadoCuenta.ACTIVA);
        c.setIntentosFallidos((short) 0);
        c.setCreadoEn(ahora);
        c = cuentas.save(c);

        CuentaPaciente vinculo = new CuentaPaciente();
        vinculo.setCuenta(c);
        vinculo.setPaciente(propio);
        vinculo.setConsentimientoEn(ahora);
        vinculos.save(vinculo);

        if (vinculado) {
            // La auditoría se escribe en su propia transacción, que aún no ve la cuenta nueva: va sin id_cuenta.
            auditoria.registrar(null, AccionAuditoria.VINCULO_PACIENTE, "PACIENTE",
                String.valueOf(propio.getIdPaciente()), "cuenta " + correo, null);
        }
        return c.getIdCuenta();
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /**
    Vincula a la cuenta con otra persona para agendarle citas (un familiar o alguien a su cargo). Si el documento ya
    existe, se usa ese paciente sin cambiar sus datos, siempre que la fecha de nacimiento coincida; si no existe, se
    crea sin teléfono. El teléfono que escribe la cuenta nunca se guarda en el paciente: el consultorio llama a la
    cuenta. Exige la declaración de consentimiento; si el paciente revocó el vínculo, no se puede volver a crear.
    */
    @Transactional
    public ParaQuien vincularTercero(Long idCuenta, TerceroForm f) {
        java.util.List<String> faltan = new java.util.ArrayList<>();
        if (f.tipoDocumento() == null) {
            faltan.add("tipo de documento");
        }
        if (vacio(f.nroDocumento())) {
            faltan.add("número de documento");
        }
        if (vacio(f.nombres())) {
            faltan.add("nombres");
        }
        if (vacio(f.apellidos())) {
            faltan.add("apellidos");
        }
        if (f.fechaNacimiento() == null) {
            faltan.add("fecha de nacimiento");
        }
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException("Falta completar: " + String.join(", ", faltan) + ".");
        }
        if (f.fechaNacimiento().isAfter(LocalDateTime.now(reloj).toLocalDate())) {
            throw new ReglaNegocioException("Fecha de nacimiento: no puede ser una fecha futura.");
        }
        if (!Boolean.TRUE.equals(f.consentimiento())) {
            throw new ReglaNegocioException(
                "Consentimiento: marca la casilla que declara que el paciente te autorizó a agendar a su nombre.");
        }
        Cuenta cuenta = cuentas.findById(idCuenta).orElseThrow(() -> new ReglaNegocioException("La cuenta no existe."));
        String nro = f.nroDocumento().trim();
        Paciente existente = pacientes.findByTipoDocumentoAndNroDocumento(f.tipoDocumento(), nro).orElse(null);
        String aviso = null;
        Paciente paciente;
        if (existente == null) {
            paciente = new Paciente();
            paciente.setTipoDocumento(f.tipoDocumento());
            paciente.setNroDocumento(nro);
            paciente.setNombres(f.nombres().trim());
            paciente.setApellidos(f.apellidos().trim());
            paciente.setFechaNacimiento(f.fechaNacimiento());
            paciente = pacientes.save(paciente);
        } else if (!existente.getFechaNacimiento().equals(f.fechaNacimiento())) {
            throw new ReglaNegocioException("Documento y fecha de nacimiento: ya hay un paciente registrado con el "
                + f.tipoDocumento() + " " + nro + " y la fecha de nacimiento no coincide. Revisa ambos datos; si están "
                + "bien, comunícate con el consultorio.");
        } else {
            paciente = existente;
            if (!vacio(f.telefono()) && !digitos(f.telefono()).equals(digitos(existente.getTelefono()))) {
                aviso = "Para actualizar el teléfono del paciente, debe comunicarse con recepción.";
            }
        }
        if (cuenta.getPacientePropio() != null && cuenta.getPacientePropio().getIdPaciente().equals(
                paciente.getIdPaciente())) {
            return new ParaQuien(paciente.getIdPaciente(), nombre(paciente), false, null);
        }
        Long idPaciente = paciente.getIdPaciente();
        CuentaPaciente vinculo = vinculos.delPaciente(idPaciente).stream()
            .filter(v -> v.getCuenta().getIdCuenta().equals(idCuenta)).findFirst().orElse(null);
        if (vinculo != null && vinculo.getRevocadoEn() != null) {
            throw new ReglaNegocioException("Este paciente retiró su consentimiento. Comunícate con el consultorio.");
        }
        if (vinculo == null) {
            vinculo = new CuentaPaciente();
            vinculo.setCuenta(cuenta);
            vinculo.setPaciente(paciente);
            vinculo.setConsentimientoEn(LocalDateTime.now(reloj));
            vinculos.save(vinculo);
            auditoria.registrar(idCuenta, AccionAuditoria.VINCULO_PACIENTE, "PACIENTE", String.valueOf(idPaciente),
                existente == null ? "paciente nuevo registrado por la cuenta" : "paciente existente", null);
        }
        return new ParaQuien(idPaciente, nombre(paciente), true, aviso);
    }

    /** Los terceros que la cuenta puede agendar (vínculo sin revocar), del más antiguo al más nuevo. */
    @Transactional(readOnly = true)
    public List<TerceroVista> terceros(Long idCuenta) {
        java.time.LocalDate hoy = LocalDateTime.now(reloj).toLocalDate();
        return vinculos.vigentesDeCuenta(idCuenta).stream()
            .map(v -> {
                Paciente p = v.getPaciente();
                return new TerceroVista(p.getIdPaciente(), nombre(p), p.getTipoDocumento().name(), p.getNroDocumento(),
                    Period.between(p.getFechaNacimiento(), hoy).getYears(), v.getConsentimientoEn());
            })
            .toList();
    }

    /**
    Para otra persona, eligiendo un tercero que la cuenta ya tiene: solo si el vínculo sigue vigente. Si el paciente
    retiró su consentimiento o no es de la cuenta, no se puede.
    */
    @Transactional(readOnly = true)
    public ParaQuien terceroVinculado(Long idCuenta, Long idPaciente) {
        return vinculos.vigentesDeCuenta(idCuenta).stream()
            .filter(v -> v.getPaciente().getIdPaciente().equals(idPaciente))
            .findFirst()
            .map(v -> new ParaQuien(idPaciente, nombre(v.getPaciente()), true, null))
            .orElseThrow(() -> new ReglaNegocioException(
                "Esa persona ya no está entre tus terceros. Elige otra o añádela de nuevo."));
    }

    private static String nombre(Paciente p) {
        return p.getNombres() + " " + p.getApellidos();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
