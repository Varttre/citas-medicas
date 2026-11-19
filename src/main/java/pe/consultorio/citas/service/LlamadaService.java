package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;

/**
Registra cada llamada de recepción (quién llamó, a qué número, cuándo y con qué resultado) y aplica su efecto en la
misma transacción: si el efecto falla, tampoco queda la llamada. Cada cola admite solo sus resultados.
*/
@Service
@Transactional
public class LlamadaService {

    static final Set<ResultadoLlamada> DE_CONFIRMACION = EnumSet.of(ResultadoLlamada.CONFIRMO,
        ResultadoLlamada.NO_CONTESTO, ResultadoLlamada.CANCELO, ResultadoLlamada.REPROGRAMO,
        ResultadoLlamada.NUMERO_INVALIDO);
    static final Set<ResultadoLlamada> DE_CUPO = EnumSet.of(ResultadoLlamada.ACEPTO, ResultadoLlamada.RECHAZO,
        ResultadoLlamada.NO_CONTESTO, ResultadoLlamada.NO_PUEDE_LLEGAR, ResultadoLlamada.NUMERO_INVALIDO,
        ResultadoLlamada.OTRO);

    private final RegistroLlamadaRepository llamadas;
    private final CitaRepository citas;
    private final OportunidadRepository oportunidades;
    private final CuentaRepository cuentas;
    private final ContactoService contacto;
    private final CitaEstadoService estados;
    private final ReprogramacionService reprogramacion;
    private final Clock reloj;

    public LlamadaService(RegistroLlamadaRepository llamadas, CitaRepository citas, OportunidadRepository oportunidades,
            CuentaRepository cuentas, ContactoService contacto, CitaEstadoService estados,
            ReprogramacionService reprogramacion, Clock reloj) {
        this.llamadas = llamadas;
        this.citas = citas;
        this.oportunidades = oportunidades;
        this.cuentas = cuentas;
        this.contacto = contacto;
        this.estados = estados;
        this.reprogramacion = reprogramacion;
        this.reloj = reloj;
    }

    /**
    Llamada de la cola de confirmaciones. CONFIRMO confirma la cita; CANCELO la cancela por el paciente (la
    observación es el motivo, obligatorio); REPROGRAMO solo registra (la pantalla abre la reprogramación);
    NO_CONTESTO y NUMERO_INVALIDO solo registran.
    */
    public RegistroLlamada registrarConfirmacion(Long idCita, ResultadoLlamada resultado, String observacion,
            Long actor) {
        exigir(resultado, DE_CONFIRMACION, "confirmaciones");
        if (resultado == ResultadoLlamada.CANCELO && vacio(observacion)) {
            throw new ReglaNegocioException("Indica el motivo de la cancelación.");
        }
        Cita c = cita(idCita);
        RegistroLlamada l = llamadas.saveAndFlush(nueva(TipoCola.CONFIRMACION, c, resultado, observacion, actor));
        switch (resultado) {
            case CONFIRMO -> estados.confirmar(idCita, l.getIdLlamada(), actor);
            case CANCELO -> estados.cancelarPorPaciente(idCita, actor, observacion.trim());
            default -> { }
        }
        return l;
    }

    /**
    Llamada de la cola de cupos liberados a un candidato. ACEPTO adelanta su cita al horario propuesto (y la
    oportunidad queda cubierta); los demás resultados solo se registran.
    */
    public RegistroLlamada registrarCupo(Long idOportunidad, Long idCita, int posicion, ResultadoLlamada resultado,
            String observacion, LocalDateTime inicioPropuesto, Long actor) {
        exigir(resultado, DE_CUPO, "cupos liberados");
        Cita c = cita(idCita);
        RegistroLlamada l = nueva(TipoCola.CUPO_LIBERADO, c, resultado, observacion, actor);
        l.setOportunidad(oportunidades.findById(idOportunidad)
            .orElseThrow(() -> new RecursoNoEncontradoException("Oportunidad inexistente " + idOportunidad)));
        l.setPosicionLista((short) Math.max(posicion, 1));
        l = llamadas.saveAndFlush(l);
        if (resultado == ResultadoLlamada.ACEPTO) {
            reprogramacion.adelantarPorCupo(idCita, idOportunidad, inicioPropuesto, actor);
        }
        return l;
    }

    private RegistroLlamada nueva(TipoCola cola, Cita c, ResultadoLlamada resultado, String observacion, Long actor) {
        String telefono = contacto.telefonoDe(c);
        if (vacio(telefono)) {
            throw new ReglaNegocioException("La cita no tiene un teléfono de contacto registrado.");
        }
        if (!vacio(observacion) && observacion.trim().length() > 255) {
            throw new ReglaNegocioException("La observación admite hasta 255 caracteres.");
        }
        RegistroLlamada l = new RegistroLlamada();
        l.setCuentaOperador(cuentas.getReferenceById(actor));
        l.setTipoCola(cola);
        l.setCita(c);
        l.setTelefonoLlamado(telefono);
        l.setResultado(resultado);
        l.setObservacion(vacio(observacion) ? null : observacion.trim());
        l.setFechaHora(LocalDateTime.now(reloj));
        return l;
    }

    private Cita cita(Long idCita) {
        return citas.findById(idCita).orElseThrow(() -> new RecursoNoEncontradoException("Cita inexistente " + idCita));
    }

    private static void exigir(ResultadoLlamada resultado, Set<ResultadoLlamada> permitidos, String cola) {
        if (resultado == null || !permitidos.contains(resultado)) {
            throw new ReglaNegocioException("Ese resultado no corresponde a la cola de " + cola + ".");
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
