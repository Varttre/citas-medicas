package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.repository.BloqueoTemporalRepository;

/**
Limpieza periódica (cada 5 minutos): los horarios liberados cuya hora ya llegó dejan de ofrecerse y se borran las
retenciones temporales vencidas.
*/
@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final OportunidadService oportunidades;
    private final BloqueoTemporalRepository bloqueos;
    private final CitaIndicadaService indicadas;
    private final Clock reloj;

    public TareasProgramadas(OportunidadService oportunidades, BloqueoTemporalRepository bloqueos,
            CitaIndicadaService indicadas, Clock reloj) {
        this.oportunidades = oportunidades;
        this.bloqueos = bloqueos;
        this.indicadas = indicadas;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void limpiar() {
        int expiradas = oportunidades.expirarVencidas();
        int borrados = bloqueos.borrarVencidos(LocalDateTime.now(reloj));
        int vencidas = indicadas.vencerVencidas();
        if (expiradas > 0 || borrados > 0 || vencidas > 0) {
            log.info("Tareas programadas: {} horarios liberados expirados, {} retenciones vencidas borradas, "
                + "{} citas indicadas vencidas", expiradas, borrados, vencidas);
        }
    }
}
