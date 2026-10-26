package pe.consultorio.citas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.FranjaHoraria;
import pe.consultorio.citas.entity.HorarioVersion;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.MedicoEspecialidad;
import pe.consultorio.citas.entity.MedicoTipoConsulta;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.Rol;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.HorarioVersionRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.PacienteRepository;
import pe.consultorio.citas.repository.RolRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/**
Crea datos de prueba en una línea, para que cada prueba arme solo lo que necesita.

Si faltan los roles o un tipo de consulta (la base de pruebas empieza vacía), los crea. Los correos y documentos se
numeran para que nunca choquen con los UNIQUE de la base.
*/
@Component
public class DatosPrueba {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private final RolRepository roles;
    private final CuentaRepository cuentas;
    private final PacienteRepository pacientes;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final TipoConsultaRepository tipos;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final MedicoTipoConsultaRepository medicoTipos;
    private final HorarioVersionRepository versiones;
    private final CitaRepository citas;
    private final ObjectProvider<Clock> reloj;

    public DatosPrueba(RolRepository roles, CuentaRepository cuentas, PacienteRepository pacientes,
            MedicoRepository medicos, EspecialidadRepository especialidades, TipoConsultaRepository tipos,
            MedicoEspecialidadRepository medicoEspecialidades, MedicoTipoConsultaRepository medicoTipos,
            HorarioVersionRepository versiones, CitaRepository citas, ObjectProvider<Clock> reloj) {
        this.roles = roles;
        this.cuentas = cuentas;
        this.pacientes = pacientes;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.tipos = tipos;
        this.medicoEspecialidades = medicoEspecialidades;
        this.medicoTipos = medicoTipos;
        this.versiones = versiones;
        this.citas = citas;
        this.reloj = reloj;
    }

    /** Especialidad activa con ese nombre. */
    @Transactional
    public Especialidad crearEspecialidad(String nombre) {
        Especialidad e = new Especialidad();
        e.setNombre(nombre + " " + SECUENCIA.getAndIncrement());
        e.setActivo(true);
        return especialidades.save(e);
    }

    /** Médico activo con su cuenta, que ejerce la especialidad y tiene la duración por defecto de cada tipo. */
    @Transactional
    public Medico crearMedico(String nombre, Especialidad e) {
        if (tipos.count() == 0) {
            tipoDePrueba();
        }
        Medico m = new Medico();
        m.setCuenta(crearCuenta(NombreRol.MEDICO, "medico" + SECUENCIA.getAndIncrement() + "@prueba.pe", null));
        m.setNombres(nombre);
        m.setApellidos("Prueba");
        m.setActivo(true);
        m = medicos.save(m);

        MedicoEspecialidad me = new MedicoEspecialidad();
        me.setMedico(m);
        me.setEspecialidad(especialidades.getReferenceById(e.getIdEspecialidad()));
        medicoEspecialidades.save(me);

        for (TipoConsulta t : tipos.findAll()) {
            MedicoTipoConsulta mt = new MedicoTipoConsulta();
            mt.setMedico(m);
            mt.setTipoConsulta(t);
            mt.setDuracionConfigurada(t.getDuracionDefecto());
            medicoTipos.save(mt);
        }
        return m;
    }

    /**
    Añade una franja de atención al médico, en la versión de horario que empieza en {@code desde} (la crea si no
    existe). {@code diaSemana}: 1 = lunes … 7 = domingo.
    */
    @Transactional
    public void crearHorario(Medico m, LocalDate desde, int diaSemana, LocalTime inicio, LocalTime fin) {
        HorarioVersion v = versiones.findAll().stream()
            .filter(x -> x.getMedico().getIdMedico().equals(m.getIdMedico()) && x.getVigenteDesde().equals(desde))
            .findFirst()
            .orElseGet(() -> {
                HorarioVersion nueva = new HorarioVersion();
                nueva.setMedico(medicos.getReferenceById(m.getIdMedico()));
                nueva.setVigenteDesde(desde);
                return nueva;
            });
        FranjaHoraria f = new FranjaHoraria();
        f.setDiaSemana((short) diaSemana);
        f.setHoraInicio(inicio);
        f.setHoraFin(fin);
        v.agregarFranja(f);
        versiones.save(v);
    }

    /** Paciente con DNI {@code nroDocumento}. */
    @Transactional
    public Paciente crearPaciente(String nroDocumento) {
        Paciente p = new Paciente();
        p.setTipoDocumento(TipoDocumento.DNI);
        p.setNroDocumento(nroDocumento);
        p.setNombres("Paciente");
        p.setApellidos(nroDocumento);
        p.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        p.setTelefono("999000111");
        return pacientes.save(p);
    }

    /** Cuenta de paciente con su paciente propio. */
    @Transactional
    public Cuenta crearCuentaPaciente(String correo) {
        Paciente propio = crearPaciente(String.format("%08d", SECUENCIA.getAndIncrement()));
        return crearCuenta(NombreRol.PACIENTE, correo, propio);
    }

    /**
    Cita presencial del paciente con el médico, en la primera especialidad del médico y con el tipo de consulta de
    prueba. Si el estado es ASISTIO o REALIZADA, rellena las marcas de tiempo que la base exige.
    */
    @Transactional
    public Cita crearCita(Medico m, Paciente p, LocalDateTime inicio, int minutos, EstadoCita estado) {
        Cita c = new Cita();
        c.setPaciente(pacientes.getReferenceById(p.getIdPaciente()));
        c.setMedico(medicos.getReferenceById(m.getIdMedico()));
        c.setEspecialidad(medicoEspecialidades.findAll().stream()
            .filter(x -> x.getId().getIdMedico().equals(m.getIdMedico()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("El médico de prueba no tiene especialidad"))
            .getEspecialidad());
        c.setTipoConsulta(tipoDePrueba());
        c.setInicio(inicio);
        c.setFin(inicio.plusMinutes(minutos));
        c.setEstado(estado);
        c.setModalidad(Modalidad.PRESENCIAL);
        c.setCanalOrigen(CanalOrigen.PRESENCIAL);
        c.setEsPrimeraVez(true);
        c.setCreadoEn(ahora());
        if (estado == EstadoCita.ASISTIO || estado == EstadoCita.REALIZADA) {
            c.setHoraLlegada(inicio);
        }
        if (estado == EstadoCita.REALIZADA) {
            c.setInicioReal(inicio);
            c.setFinReal(inicio.plusMinutes(minutos));
        }
        return citas.save(c);
    }

    private Cuenta crearCuenta(NombreRol nombreRol, String correo, Paciente propio) {
        Cuenta c = new Cuenta();
        c.setCorreo(correo);
        c.setPasswordHash("sin-uso-en-pruebas");
        c.setRol(rol(nombreRol));
        c.setPacientePropio(propio);
        c.setEstado(EstadoCuenta.ACTIVA);
        c.setIntentosFallidos((short) 0);
        c.setCreadoEn(ahora());
        return cuentas.save(c);
    }

    private Rol rol(NombreRol nombre) {
        return roles.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return roles.save(r);
        });
    }

    private TipoConsulta tipoDePrueba() {
        return tipos.findAll().stream().findFirst().orElseGet(() -> {
            TipoConsulta t = new TipoConsulta();
            t.setNombre("Consulta de prueba");
            t.setDuracionDefecto((short) 30);
            t.setAjusteMedico(true);
            t.setAjusteRecepcion(true);
            t.setReservableWeb(true);
            t.setModalidadPermitida(ModalidadPermitida.AMBAS);
            t.setActivo(true);
            return tipos.save(t);
        });
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj.getIfAvailable(Clock::systemDefaultZone));
    }
}
