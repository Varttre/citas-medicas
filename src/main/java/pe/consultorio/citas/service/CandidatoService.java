package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.Candidato;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.CandidatoPostergado;
import pe.consultorio.citas.entity.CandidatoPostergadoId;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.MedicoEspecialidadId;
import pe.consultorio.citas.entity.MedicoTipoConsultaId;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.repository.CandidatoPostergadoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoEspecialidadRepository;
import pe.consultorio.citas.repository.MedicoTipoConsultaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Para un horario liberado, la lista ordenada de pacientes a quienes recepción debe llamar.

Un paciente es candidato si aceptó que lo llamen (preferencia sin revocar), su cita sigue vigente y es posterior al
bloque, el bloque es con su médico (o con su especialidad, según lo que eligió; si es otro médico, este debe atender su
tipo de consulta), cae dentro de los días que aceptó
adelantar, llega a tiempo con su margen de llegada, no tiene otra cita que se cruce y no aceptó ya otro cupo hoy.
Se ordenan por puntaje; los que recepción postergó van al final.
*/
@Service
@Transactional(readOnly = true)
public class CandidatoService {

    static final int PESO_MISMO_MEDICO = 30;
    static final int PESO_MISMA_ESPECIALIDAD = 10;
    static final int PESO_TELECONSULTA = 15;
    static final int PESO_CALCE_MAX = 20;
    static final int PESO_ANTIGUEDAD_MAX = 15;
    static final int PESO_LLEGADA_MAX = 10;
    static final int PESO_DISPONIBLE_HOY = 5;
    private static final int LLEGADA_TOPE_MIN = 120;

    private final OportunidadRepository oportunidades;
    private final PreferenciaDisponibilidadRepository preferencias;
    private final CitaRepository citas;
    private final MedicoEspecialidadRepository medicoEspecialidades;
    private final RegistroLlamadaRepository llamadas;
    private final CandidatoPostergadoRepository postergados;
    private final CuentaRepository cuentas;
    private final ContactoService contacto;
    private final ConfiguracionService configuracion;
    private final MedicoTipoConsultaRepository medicoTipos;
    private final Clock reloj;

    public CandidatoService(OportunidadRepository oportunidades, PreferenciaDisponibilidadRepository preferencias,
            CitaRepository citas, MedicoEspecialidadRepository medicoEspecialidades, RegistroLlamadaRepository llamadas,
            CandidatoPostergadoRepository postergados, CuentaRepository cuentas, ContactoService contacto,
            ConfiguracionService configuracion, MedicoTipoConsultaRepository medicoTipos, Clock reloj) {
        this.medicoTipos = medicoTipos;
        this.oportunidades = oportunidades;
        this.preferencias = preferencias;
        this.citas = citas;
        this.medicoEspecialidades = medicoEspecialidades;
        this.llamadas = llamadas;
        this.postergados = postergados;
        this.cuentas = cuentas;
        this.contacto = contacto;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /** Candidatos para el bloque liberado, ordenados por puntaje; los postergados van al final. */
    public List<Candidato> candidatos(Long idOportunidad) {
        Oportunidad op = oportunidades.findById(idOportunidad)
            .orElseThrow(() -> new RecursoNoEncontradoException("Oportunidad inexistente " + idOportunidad));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Long idMedico = op.getMedico().getIdMedico();
        int minutosBloque = (int) minutos(op.getInicio(), op.getFin());
        Map<Long, LocalDateTime> postergadoEn = postergados.findByIdIdOportunidad(idOportunidad).stream()
            .collect(Collectors.toMap(p -> p.getId().getIdCita(), CandidatoPostergado::getPostergadoEn));

        List<Candidato> lista = new ArrayList<>();
        for (PreferenciaDisponibilidad p : preferencias.vigentes()) {
            Cita c = p.getCita();
            int duracion = (int) minutos(c.getInicio(), c.getFin());
            boolean mismoMedico = c.getMedico().getIdMedico().equals(idMedico);
            if (c.getInicio().isBefore(op.getFin())) {
                continue;                                                          // 2. cita posterior al bloque
            }
            if (p.getAlcance() == AlcancePreferencia.MEDICO ? !mismoMedico
                    : !medicoEspecialidades.existsById(new MedicoEspecialidadId(idMedico,
                        c.getEspecialidad().getIdEspecialidad()))) {
                continue;                                                          // 3. alcance
            }
            if (!mismoMedico && !medicoTipos.existsById(new MedicoTipoConsultaId(idMedico,
                    c.getTipoConsulta().getIdTipoConsulta()))) {
                continue;                                                          // 3b. otro médico: su tipo
            }
            if (op.getInicio().toLocalDate().isBefore(c.getInicio().toLocalDate().minusDays(p.getDiasPrevios()))) {
                continue;                                                          // 4. días que acepta adelantar
            }
            LocalDateTime llegaDesde = Rejilla.redondearArriba(ahora.plusMinutes(p.getMinLlegadaMin()),
                configuracion.incremento());
            LocalDateTime propuesto = llegaDesde.isAfter(op.getInicio()) ? llegaDesde : op.getInicio();
            LocalDateTime propuestoFin = propuesto.plusMinutes(duracion);
            if (propuestoFin.isAfter(op.getFin())) {
                continue;                                                          // 5. llega a tiempo y cabe
            }
            Long idPaciente = c.getPaciente().getIdPaciente();
            boolean seCruza = citas.delPaciente(idPaciente).stream()
                .filter(x -> !x.getIdCita().equals(c.getIdCita()))
                .filter(x -> !CitaRepository.CANCELADAS.contains(x.getEstado()))
                .anyMatch(x -> x.getInicio().isBefore(propuestoFin) && x.getFin().isAfter(propuesto));
            if (seCruza) {
                continue;                                                          // 6. otra cita en ese horario
            }
            if (llamadas.aceptoCupoDesde(idPaciente, ahora.toLocalDate().atStartOfDay())) {
                continue;                                                          // 7. ya aceptó un cupo hoy
            }
            String telefono = contacto.telefonoDe(c);
            int puntaje = calcularPuntaje(mismoMedico, p.getAlcance(), c.getModalidad(), duracion, minutosBloque,
                Duration.between(p.getInscritoEn(), ahora).toDays(), p.getMinLlegadaMin(),
                p.getConsentidoEn().toLocalDate().equals(op.getInicio().toLocalDate()));
            lista.add(new Candidato(c.getIdCita(), idPaciente,
                c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(), telefono,
                contacto.telefonoInvalido(telefono), p.getAlcance(), p.getMinLlegadaMin(), duracion,
                minutosBloque - duracion, p.getInscritoEn(), c.getModalidad(), c.getInicio(), propuesto, puntaje,
                postergadoEn.containsKey(c.getIdCita()), 0));
        }

        Comparator<Candidato> noPostergados = Comparator.comparingInt(Candidato::puntaje).reversed()
            .thenComparing(Candidato::inscritoEn);
        List<Candidato> ordenados = new ArrayList<>(lista.stream().filter(x -> !x.postergado())
            .sorted(noPostergados).toList());
        ordenados.addAll(lista.stream().filter(Candidato::postergado)
            .sorted(Comparator.comparing(x -> postergadoEn.get(x.idCita()))).toList());
        List<Candidato> conPosicion = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Candidato x = ordenados.get(i);
            conPosicion.add(new Candidato(x.idCita(), x.idPaciente(), x.paciente(), x.telefonoContacto(),
                x.telefonoInvalido(), x.alcance(), x.minLlegadaMin(), x.duracionMin(), x.residuoMin(), x.inscritoEn(),
                x.modalidad(), x.citaActual(), x.inicioPropuesto(), x.puntaje(), x.postergado(), i + 1));
        }
        return conPosicion;
    }

    /**
    Las horas a las que se puede pasar la cita del candidato dentro del bloque: desde la primera a la que llega a
    tiempo ({@code inicioPropuesto}) hasta la última en que todavía cabe, cada {@code incremento} minutos, sin cruzarse
    con otra cita suya. Así recepción no queda atada al inicio del bloque si al paciente le conviene otra hora.
    */
    public List<LocalDateTime> horasPosibles(Long idOportunidad, Candidato ca) {
        Oportunidad op = oportunidades.findById(idOportunidad)
            .orElseThrow(() -> new RecursoNoEncontradoException("Oportunidad inexistente " + idOportunidad));
        int paso = Math.max(configuracion.incremento(), 5);
        List<Cita> otras = citas.delPaciente(ca.idPaciente()).stream()
            .filter(x -> !x.getIdCita().equals(ca.idCita()))
            .filter(x -> !CitaRepository.CANCELADAS.contains(x.getEstado()))
            .toList();
        List<LocalDateTime> horas = new ArrayList<>();
        for (LocalDateTime h = ca.inicioPropuesto(); !h.plusMinutes(ca.duracionMin()).isAfter(op.getFin());
                h = h.plusMinutes(paso)) {
            LocalDateTime ini = h;
            LocalDateTime fin = h.plusMinutes(ca.duracionMin());
            if (otras.stream().noneMatch(x -> x.getInicio().isBefore(fin) && x.getFin().isAfter(ini))) {
                horas.add(h);
            }
        }
        return horas;
    }

    /**
    Recepción deja a un candidato para el final de la lista de ese bloque. Se puede repetir sobre el mismo candidato
    (vuelve a quedar último); si ya es el último, no cambia nada y devuelve false. Solo ordena la lista: no cuenta como llamada ni entra
    en ninguna métrica.
    */
    @Transactional
    public boolean postergar(Long idOportunidad, Long idCita, Long actor) {
        List<Candidato> lista = candidatos(idOportunidad);
        if (lista.isEmpty() || lista.get(lista.size() - 1).idCita().equals(idCita)) {
            return false;
        }
        CandidatoPostergadoId id = new CandidatoPostergadoId();
        id.setIdOportunidad(idOportunidad);
        id.setIdCita(idCita);
        CandidatoPostergado p = postergados.findById(id).orElseGet(() -> {
            CandidatoPostergado nuevo = new CandidatoPostergado();
            nuevo.setOportunidad(oportunidades.getReferenceById(idOportunidad));
            nuevo.setCita(citas.getReferenceById(idCita));
            return nuevo;
        });
        p.setCuentaOperador(cuentas.getReferenceById(actor));
        p.setPostergadoEn(LocalDateTime.now(reloj));
        postergados.save(p);
        return true;
    }

    /**
    Puntaje de un candidato: suma pesos fijos por mismo médico (o misma especialidad), teleconsulta, cuánto llena el
    bloque, antigüedad en la lista, cuánto margen de llegada pide y si consintió el mismo día del bloque.
    */
    static int calcularPuntaje(boolean mismoMedico, AlcancePreferencia alcance, Modalidad modalidad, int duracionMin,
            int minutosBloque, long diasInscrito, int minLlegadaMin, boolean disponibleHoy) {
        int puntaje = 0;
        if (mismoMedico) {
            puntaje += PESO_MISMO_MEDICO;
        } else if (alcance == AlcancePreferencia.ESPECIALIDAD) {
            puntaje += PESO_MISMA_ESPECIALIDAD;
        }
        if (modalidad == Modalidad.TELECONSULTA) {
            puntaje += PESO_TELECONSULTA;
        }
        puntaje += minutosBloque <= 0 ? 0 : PESO_CALCE_MAX * duracionMin / minutosBloque;
        puntaje += (int) Math.min(Math.max(diasInscrito, 0), PESO_ANTIGUEDAD_MAX);
        puntaje += PESO_LLEGADA_MAX * (LLEGADA_TOPE_MIN - Math.min(minLlegadaMin, LLEGADA_TOPE_MIN)) / LLEGADA_TOPE_MIN;
        if (disponibleHoy) {
            puntaje += PESO_DISPONIBLE_HOY;
        }
        return puntaje;
    }

    private static long minutos(LocalDateTime desde, LocalDateTime hasta) {
        return Duration.between(desde, hasta).toMinutes();
    }
}
