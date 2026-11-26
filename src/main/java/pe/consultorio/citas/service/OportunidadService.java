package pe.consultorio.citas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.disponibilidad.DisponibilidadService;
import pe.consultorio.citas.service.disponibilidad.Rejilla;

/**
Registra los horarios liberados como bloques completos y los cierra cuando alguien los ocupa.

Un bloque liberado es todo el tramo libre que rodea al horario soltado: si 11:30–12:00 ya estaba libre y se cancela
12:00–12:30, la oportunidad es 11:30–12:30. Si ya había una oportunidad abierta dentro de ese tramo, queda REEMPLAZADA
por la nueva, más grande. Cuando una cita ocupa parte de una oportunidad, esta queda CUBIERTA y los sobrantes útiles
se vuelven a ofrecer como oportunidades nuevas.
*/
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OportunidadService {

    private final OportunidadRepository oportunidades;
    private final DisponibilidadService disponibilidad;
    private final ConfiguracionService configuracion;
    private final Clock reloj;

    public OportunidadService(OportunidadRepository oportunidades, DisponibilidadService disponibilidad,
            ConfiguracionService configuracion, Clock reloj) {
        this.oportunidades = oportunidades;
        this.disponibilidad = disponibilidad;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    /**
    Registra el bloque libre que contiene {@code [inicio, fin)} tras cancelar, reprogramar o acortar la cita. Se llama
    dentro de la misma transacción, cuando la cita ya dejó ese horario.
    */
    public void liberar(Cita cita, LocalDateTime inicio, LocalDateTime fin) {
        liberar(cita, cita.getMedico(), inicio, fin);
    }

    /**
    Igual que {@link #liberar(Cita, LocalDateTime, LocalDateTime)}, pero en la agenda de {@code medico}: se usa cuando
    la cita acaba de cambiar de médico (adelanto por un cupo de su especialidad) y el horario soltado es del anterior.
    */
    public void liberar(Cita cita, Medico medico, LocalDateTime inicio, LocalDateTime fin) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (!fin.isAfter(ahora)) {
            return;
        }
        LocalDateTime desdeAhora = Rejilla.redondearArriba(ahora, configuracion.incremento());
        LocalDateTime punto = inicio.isAfter(desdeAhora) ? inicio : desdeAhora;
        Long idMedico = medico.getIdMedico();
        RangeSet<LocalDateTime> libres = disponibilidad.bloquesLibresDelDia(idMedico, inicio.toLocalDate());
        Range<LocalDateTime> contiene = libres.rangeContaining(punto);
        if (contiene == null) {
            return;
        }
        LocalDateTime bInicio = contiene.lowerEndpoint().isBefore(desdeAhora) ? desdeAhora : contiene.lowerEndpoint();
        LocalDateTime bFin = contiene.upperEndpoint();
        if (minutos(bInicio, bFin) < configuracion.huecoMinimoUtil()) {
            return;
        }
        Oportunidad padre = null;
        for (Oportunidad o : oportunidades.abiertasQueCruzan(idMedico, bInicio, bFin)) {
            o.setEstado(EstadoOportunidad.REEMPLAZADA);
            o.setCerradaEn(ahora);
            if (padre == null) {
                padre = o;
            }
        }
        oportunidades.save(nueva(cita, medico, bInicio, bFin, padre, ahora));
    }

    /** Marca como cubiertos los bloques abiertos que ocupa la cita nueva y vuelve a ofrecer lo que sobra. */
    public void registrarCobertura(Cita nueva) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int hueco = configuracion.huecoMinimoUtil();
        for (Oportunidad o : oportunidades.abiertasQueCruzan(nueva.getMedico().getIdMedico(), nueva.getInicio(),
                nueva.getFin())) {
            o.setEstado(EstadoOportunidad.CUBIERTA);
            o.setCerradaEn(ahora);
            o.setCitaCobertura(nueva);
            oportunidades.saveAndFlush(o);
            if (minutos(o.getInicio(), nueva.getInicio()) >= hueco) {
                oportunidades.save(nueva(o.getCitaOrigen(), o.getMedico(), o.getInicio(), nueva.getInicio(), o,
                    ahora));
            }
            if (minutos(nueva.getFin(), o.getFin()) >= hueco) {
                oportunidades.save(nueva(o.getCitaOrigen(), o.getMedico(), nueva.getFin(), o.getFin(), o, ahora));
            }
        }
    }

    /**
    Bloques abiertos que aún no empiezan, los más próximos primero. La tarea programada los marca como expirados cada
    pocos minutos; mientras tanto (o justo después de arrancar la aplicación) no se deben ofrecer horarios pasados.
    */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<Oportunidad> abiertas() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return oportunidades.abiertas().stream().filter(o -> o.getInicio().isAfter(ahora)).toList();
    }

    /** Las que se cubrieron hoy, la más reciente primero (recepción las ve aparte, ya sin ofrecerlas). */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<Oportunidad> cubiertasHoy() {
        return oportunidades.cubiertasDesde(LocalDateTime.now(reloj).toLocalDate().atStartOfDay());
    }

    /** Las abiertas cuya hora ya llegó dejan de ofrecerse. Devuelve cuántas expiraron. */
    @Transactional(propagation = Propagation.REQUIRED)
    public int expirarVencidas() {
        return oportunidades.expirar(LocalDateTime.now(reloj));
    }

    private static Oportunidad nueva(Cita origen, Medico medico, LocalDateTime inicio, LocalDateTime fin,
            Oportunidad padre, LocalDateTime ahora) {
        Oportunidad o = new Oportunidad();
        o.setCitaOrigen(origen);
        o.setMedico(medico);
        o.setOportunidadPadre(padre);
        o.setInicio(inicio);
        o.setFin(fin);
        o.setEstado(EstadoOportunidad.ABIERTA);
        o.setCreadaEn(ahora);
        return o;
    }

    private static long minutos(LocalDateTime desde, LocalDateTime hasta) {
        return Duration.between(desde, hasta).toMinutes();
    }
}
