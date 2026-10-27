package pe.consultorio.citas.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Aviso;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.FranjaHoraria;
import pe.consultorio.citas.entity.HorarioVersion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidad;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.AvisoRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.HorarioVersionRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.RolRepository;
import pe.consultorio.citas.service.ConfiguracionService;

/**
Usuarios de prueba para desarrollo (perfil dev). Si la base no tiene ninguna cuenta, crea un usuario por perfil con
la contraseña Demo2026!, dos médicos con su especialidad, duraciones y horario vigente desde hoy, y dos avisos.
En una instalación real no corre: las cuentas las crea el administrador.
*/
@Component
@Profile("dev")
public class DatosDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);
    private static final String CONTRASENA = "Demo2026!";

    private final CuentaRepository cuentas;
    private final RolRepository roles;
    private final PacienteRepository pacientes;
    private final CuentaPacienteRepository cuentaPacientes;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final MedicoTipoConsultaRepository medicoTipos;
    private final HorarioVersionRepository versiones;
    private final AvisoRepository avisos;
    private final ConfiguracionService configuracion;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    public DatosDemo(CuentaRepository cuentas, RolRepository roles, PacienteRepository pacientes,
            CuentaPacienteRepository cuentaPacientes, MedicoRepository medicos, EspecialidadRepository especialidades,
            MedicoEspecialidadRepository medicoEspecialidades, MedicoTipoConsultaRepository medicoTipos,
            HorarioVersionRepository versiones, AvisoRepository avisos, ConfiguracionService configuracion,
            PasswordEncoder encoder, Clock reloj) {
        this.cuentas = cuentas;
        this.roles = roles;
        this.pacientes = pacientes;
        this.cuentaPacientes = cuentaPacientes;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.medicoEspecialidades = medicoEspecialidades;
        this.medicoTipos = medicoTipos;
        this.versiones = versiones;
        this.avisos = avisos;
        this.configuracion = configuracion;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (cuentas.count() > 0) {
            return;
        }
        cuenta("superadmin@consultorio.pe", NombreRol.SUPERADMIN, "Superadministrador", null);
        cuenta("admin@consultorio.pe", NombreRol.ADMINISTRADOR, "Administrador", null);
        cuenta("recepcion@consultorio.pe", NombreRol.RECEPCION, "Recepción", null);

        Medico ramirez = medico("medico1@consultorio.pe", "Carmen", "Ramírez", "Cardiología");
        for (int dia = 1; dia <= 5; dia++) {
            franja(ramirez, dia, "08:00", "13:00");
            franja(ramirez, dia, "15:00", "19:00");
        }
        Medico ponce = medico("medico2@consultorio.pe", "Andrés", "Ponce", "Medicina General");
        for (int dia : new int[] {1, 3, 5}) {
            franja(ponce, dia, "09:00", "13:00");
        }

        Paciente lucia = new Paciente();
        lucia.setTipoDocumento(TipoDocumento.DNI);
        lucia.setNroDocumento("12345678");
        lucia.setNombres("Lucía");
        lucia.setApellidos("Vega Ríos");
        lucia.setFechaNacimiento(LocalDate.of(1990, 5, 14));
        lucia.setTelefono("987654321");
        lucia.setCorreo("paciente@consultorio.pe");
        lucia = pacientes.save(lucia);
        Cuenta cuentaLucia = cuenta("paciente@consultorio.pe", NombreRol.PACIENTE, null, lucia);
        CuentaPaciente vinculo = new CuentaPaciente();
        vinculo.setCuenta(cuentaLucia);
        vinculo.setPaciente(lucia);
        vinculo.setConsentimientoEn(ahora());
        cuentaPacientes.save(vinculo);

        aviso("Campaña de chequeo cardiológico", "Durante noviembre, evaluación cardiológica con descuento para mayores de 50 años.");
        aviso("Nuevo horario de atención", "Atendemos de lunes a viernes de 8:00 a 19:00. Reserva tu cita desde el portal.");

        log.info("Datos de prueba creados: 6 usuarios con la contraseña {}", CONTRASENA);
    }

    private Cuenta cuenta(String correo, NombreRol rol, String nombreVisible, Paciente propio) {
        Cuenta c = new Cuenta();
        c.setCorreo(correo);
        c.setNombreVisible(nombreVisible);
        c.setPasswordHash(encoder.encode(CONTRASENA));
        c.setRol(roles.findByNombre(rol).orElseThrow());
        c.setPacientePropio(propio);
        c.setEstado(EstadoCuenta.ACTIVA);
        c.setIntentosFallidos((short) 0);
        c.setCreadoEn(ahora());
        return cuentas.save(c);
    }

    /** Médico activo con su especialidad y la duración por defecto de cada tipo activo. */
    private Medico medico(String correo, String nombres, String apellidos, String especialidad) {
        Medico m = new Medico();
        m.setCuenta(cuenta(correo, NombreRol.MEDICO, null, null));
        m.setNombres(nombres);
        m.setApellidos(apellidos);
        m.setActivo(true);
        m = medicos.save(m);

        Especialidad e = especialidades.findAll().stream()
            .filter(x -> x.getNombre().equals(especialidad))
            .findFirst()
            .orElseThrow();
        MedicoEspecialidad me = new MedicoEspecialidad();
        me.setMedico(m);
        me.setEspecialidad(e);
        medicoEspecialidades.save(me);

        for (TipoConsulta t : configuracion.tiposActivos()) {
            MedicoTipoConsulta mt = new MedicoTipoConsulta();
            mt.setMedico(m);
            mt.setTipoConsulta(t);
            mt.setDuracionConfigurada(t.getDuracionDefecto());
            medicoTipos.save(mt);
        }
        return m;
    }

    /** Añade una franja a la versión de horario del médico que empieza hoy. */
    private void franja(Medico m, int diaSemana, String inicio, String fin) {
        LocalDate hoy = LocalDate.now(reloj);
        HorarioVersion v = versiones.findAll().stream()
            .filter(x -> x.getMedico().getIdMedico().equals(m.getIdMedico()))
            .findFirst()
            .orElseGet(() -> {
                HorarioVersion nueva = new HorarioVersion();
                nueva.setMedico(m);
                nueva.setVigenteDesde(hoy);
                return nueva;
            });
        FranjaHoraria f = new FranjaHoraria();
        f.setDiaSemana((short) diaSemana);
        f.setHoraInicio(LocalTime.parse(inicio));
        f.setHoraFin(LocalTime.parse(fin));
        v.agregarFranja(f);
        versiones.save(v);
    }

    private void aviso(String titulo, String texto) {
        Aviso a = new Aviso();
        a.setBannerUrl("https://placehold.co/800x200?text=Consultorio");
        a.setTitulo(titulo);
        a.setTexto(texto);
        a.setEnlace("/paciente/cita/nueva");
        a.setActivo(true);
        avisos.save(a);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
