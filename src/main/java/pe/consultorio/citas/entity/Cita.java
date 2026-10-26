package pe.consultorio.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Reserva. Conserva su identificador al reprogramarse. Tabla cita. */
@Entity
@Table(name = "cita")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita", updatable = false)
    private Long idCita;

    /** Paciente atendido y dueño de la cita, sea cual sea el canal por el que se creó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    /**
    Cuenta que agendó y contacto de la cita. NULL si recepción agendó sin cuenta titular; entonces
    el contacto es PACIENTE.telefono.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cuenta_titular")
    private Cuenta cuentaTitular;

    /** Médico que atiende. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medico")
    private Medico medico;

    /** Especialidad por la que se atiende. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_especialidad")
    private Especialidad especialidad;

    /** Tipo de consulta. Define la duración prevista y el segmento de estimación. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_consulta")
    private TipoConsulta tipoConsulta;

    /** Programado. Duración = fin − inicio. */
    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    /** Programado. */
    @Column(name = "fin", nullable = false)
    private LocalDateTime fin;

    /**
    Estado actual. El historial está en CITA_EVENTO. ASISTIO = recepción registró la llegada;
    REALIZADA = el médico registró el fin de la atención.
    */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoCita estado;

    /** PRESENCIAL, TELECONSULTA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 12)
    private Modalidad modalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_origen", nullable = false, length = 15)
    private CanalOrigen canalOrigen;

    /** Calculado por el sistema al agendar. */
    @Column(name = "es_primera_vez", nullable = false)
    private boolean esPrimeraVez;

    /** Registrada por recepción. */
    @Column(name = "hora_llegada")
    private LocalDateTime horaLlegada;

    /** Registrado por el médico. */
    @Column(name = "inicio_real")
    private LocalDateTime inicioReal;

    /** Registrado por el médico. */
    @Column(name = "fin_real")
    private LocalDateTime finReal;

    /** Indicaciones y observaciones del médico. Visibles para el paciente al expandir la cita. */
    @Column(name = "indicaciones")
    private String indicaciones;

    /**
    Indicaciones administrativas de recepción (qué traer, a qué hora llegar). Visibles para el
    paciente junto a las del médico.
    */
    @Column(name = "indicaciones_admin")
    private String indicacionesAdmin;

    /** Cuándo se registró la cita. */
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public Long getIdCita() {
        return idCita;
    }

    public void setIdCita(Long idCita) {
        this.idCita = idCita;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public Cuenta getCuentaTitular() {
        return cuentaTitular;
    }

    public void setCuentaTitular(Cuenta cuentaTitular) {
        this.cuentaTitular = cuentaTitular;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(Especialidad especialidad) {
        this.especialidad = especialidad;
    }

    public TipoConsulta getTipoConsulta() {
        return tipoConsulta;
    }

    public void setTipoConsulta(TipoConsulta tipoConsulta) {
        this.tipoConsulta = tipoConsulta;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFin() {
        return fin;
    }

    public void setFin(LocalDateTime fin) {
        this.fin = fin;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public void setEstado(EstadoCita estado) {
        this.estado = estado;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public void setModalidad(Modalidad modalidad) {
        this.modalidad = modalidad;
    }

    public CanalOrigen getCanalOrigen() {
        return canalOrigen;
    }

    public void setCanalOrigen(CanalOrigen canalOrigen) {
        this.canalOrigen = canalOrigen;
    }

    public boolean isEsPrimeraVez() {
        return esPrimeraVez;
    }

    public void setEsPrimeraVez(boolean esPrimeraVez) {
        this.esPrimeraVez = esPrimeraVez;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalDateTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public LocalDateTime getInicioReal() {
        return inicioReal;
    }

    public void setInicioReal(LocalDateTime inicioReal) {
        this.inicioReal = inicioReal;
    }

    public LocalDateTime getFinReal() {
        return finReal;
    }

    public void setFinReal(LocalDateTime finReal) {
        this.finReal = finReal;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public void setIndicaciones(String indicaciones) {
        this.indicaciones = indicaciones;
    }

    public String getIndicacionesAdmin() {
        return indicacionesAdmin;
    }

    public void setIndicacionesAdmin(String indicacionesAdmin) {
        this.indicacionesAdmin = indicacionesAdmin;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
