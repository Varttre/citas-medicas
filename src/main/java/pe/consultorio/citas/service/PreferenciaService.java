package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.PreferenciaDisponibilidad;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;

/** Guarda o retira el permiso del paciente para que lo llamen si se libera un horario antes de su cita. */
@Service
public class PreferenciaService {

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

    /** Crea la preferencia de la cita o la actualiza sin cambiar su antigüedad en la lista. */
    @Transactional
    public void guardar(Long idCita, PreferenciaDatos d, Long idCuentaActor) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
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
}
