package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;

/** Guarda o retira el permiso del paciente para que lo llamen si se libera un horario antes de su cita. */
@Service
public class PreferenciaService {

    /** Mensaje cuando la cita se sacó (o reprogramó) con menos anticipación que la mínima. */
    public static final String NO_ELEGIBLE = "Esta cita se sacó con poca anticipación: no admite «Acepto ser llamado».";

    private final PreferenciaDisponibilidadRepository preferencias;
    private final CitaRepository citas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public PreferenciaService(PreferenciaDisponibilidadRepository preferencias, CitaRepository citas,
            AuditoriaService auditoria, Clock reloj) {
        this.preferencias = preferencias;
        this.citas = citas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
    Crea la preferencia de la cita o la actualiza sin cambiar su antigüedad en la lista. Solo si la cita es elegible
    (se sacó con la anticipación mínima, RC-046); los días van de 1 a los que faltan para la cita (si se piden más,
    se guardan los que faltan: es lo mismo que «Lo más pronto posible»).
    */
    @Transactional
    public void guardar(Long idCita, PreferenciaDatos d, Long idCuentaActor) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Cita cita = citas.findById(idCita)
            .orElseThrow(() -> new ReglaNegocioException("La cita no existe."));
        if (!cita.isElegibleAdelanto()) {
            throw new ReglaNegocioException(NO_ELEGIBLE);
        }
        if (d.diasPrevios() < 1) {
            throw new ReglaNegocioException("«Hasta cuántos días antes»: elige al menos 1 día.");
        }
        long faltan = ChronoUnit.DAYS.between(ahora.toLocalDate(), cita.getInicio().toLocalDate());
        if (faltan < 1) {
            throw new ReglaNegocioException("La cita es hoy: ya no se puede adelantar.");
        }
        d = new PreferenciaDatos(d.alcance(), d.minLlegadaMin(), (int) Math.min(d.diasPrevios(), faltan));
        PreferenciaDisponibilidad p = preferencias.findByCitaIdCita(idCita).orElse(null);
        String anterior = "sin preferencia";
        if (p == null) {
            p = new PreferenciaDisponibilidad();
            p.setCita(citas.getReferenceById(idCita));
            p.setInscritoEn(ahora);
        } else {
            anterior = p.getAlcance() + ", " + p.getMinLlegadaMin() + " min, " + p.getDiasPrevios() + " días";
        }
        p.setAlcance(d.alcance());
        p.setMinLlegadaMin((short) d.minLlegadaMin());
        p.setDiasPrevios((short) d.diasPrevios());
        p.setConsentidoEn(ahora);
        p.setRevocadoEn(null);
        preferencias.save(p);
        auditoria.registrar(idCuentaActor, AccionAuditoria.PREFERENCIA_CAMBIO, "CITA", String.valueOf(idCita),
            anterior + " → " + d.alcance() + ", " + d.minLlegadaMin() + " min, " + d.diasPrevios() + " días", null);
    }

    /** Retira la preferencia de la cita. */
    @Transactional
    public void revocar(Long idCita, Long idCuentaActor) {
        preferencias.findByCitaIdCita(idCita).ifPresent(p -> {
            p.setRevocadoEn(LocalDateTime.now(reloj));
            auditoria.registrar(idCuentaActor, AccionAuditoria.PREFERENCIA_CAMBIO, "CITA", String.valueOf(idCita),
                "preferencia revocada", null);
        });
    }

    /** Preferencia de disponibilidad inmediata de la cita, si sigue vigente (sin revocar ni consumir). */
    @Transactional(readOnly = true)
    public java.util.Optional<pe.consultorio.citas.entity.PreferenciaDisponibilidad> vigenteDe(Long idCita) {
        return preferencias.findByCitaIdCita(idCita).filter(p -> p.getRevocadoEn() == null);
    }

    /** Da por usada la preferencia de la cita: el paciente ya aceptó un cupo liberado y su cita se adelantó. */
    @Transactional
    public void consumir(Long idCita, Long idCuentaActor) {
        preferencias.findByCitaIdCita(idCita).filter(p -> p.getRevocadoEn() == null).ifPresent(p -> {
            p.setRevocadoEn(LocalDateTime.now(reloj));
            auditoria.registrar(idCuentaActor, AccionAuditoria.PREFERENCIA_CAMBIO, "CITA", String.valueOf(idCita),
                "preferencia consumida", "Consumida al aceptar un cupo");
        });
    }
}
