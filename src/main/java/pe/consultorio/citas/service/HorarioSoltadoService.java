package pe.consultorio.citas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.repository.CitaEventoRepository;

/**
Reconstruye el horario que realmente se soltó detrás de un horario liberado.

El horario liberado que se ofrece es el tramo libre que rodea al horario soltado (se cancela 08:15–08:45 y se ofrece
08:00–09:00). Recepción necesita ver los dos, y las métricas solo cuentan como recuperado lo que cae dentro del
horario soltado. No se guarda aparte: se deduce del último evento de la cita de origen anterior a la creación del
bloque. Los sobrantes que quedan tras una cobertura parcial heredan el horario soltado de su bloque original.
*/
@Service
@Transactional(readOnly = true)
public class HorarioSoltadoService {

    /**
    Un horario soltado.

    @param idRaiz  id del bloque que se creó al soltarlo; sirve para no contar dos veces el mismo horario cuando se
                   cubre en partes
    @param origen  qué lo soltó, p. ej. «Canceló Carmen Ramírez»
    */
    public record Soltado(Long idRaiz, LocalDateTime inicio, LocalDateTime fin, String origen) { }

    private final CitaEventoRepository eventos;

    public HorarioSoltadoService(CitaEventoRepository eventos) {
        this.eventos = eventos;
    }

    /** El horario que soltó la cita de origen del bloque, o vacío si no se puede reconstruir. */
    public Optional<Soltado> de(Oportunidad o) {
        Oportunidad raiz = raiz(o);
        Cita c = raiz.getCitaOrigen();
        List<CitaEvento> previos = eventos.findByCitaIdCitaAndFechaHoraLessThanEqualOrderByFechaHoraDesc(
            c.getIdCita(), raiz.getCreadaEn());
        if (previos.isEmpty()) {
            return Optional.empty();
        }
        CitaEvento e = previos.get(0);
        String paciente = c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos();
        Long id = raiz.getIdOportunidad();
        return Optional.of(switch (e.getTipoEvento()) {
            case REPROGRAMACION -> new Soltado(id, e.getInicioAnterior(), e.getFinAnterior(),
                "Se movió la cita de " + paciente);
            case AJUSTE_DURACION -> new Soltado(id, e.getFinNuevo(), e.getFinAnterior(),
                "Se acortó la cita de " + paciente);
            default -> new Soltado(id, c.getInicio(), c.getFin(),
                (e.getEstadoNuevo() == EstadoCita.CANCELADA_POR_CLINICA ? "El consultorio canceló la cita de "
                    : "Canceló ") + paciente);
        });
    }

    /**
    Todos los horarios soltados que contiene el bloque: el suyo y los de los bloques abiertos que absorbió al crearse
    (quedaron REEMPLAZADOS por él). Así, si se cancelan dos citas seguidas y alguien ocupa el bloque unido, se
    recuperan los dos horarios.
    */
    public List<Soltado> todos(Oportunidad o) {
        List<Soltado> lista = new ArrayList<>();
        Oportunidad actual = o;
        while (actual != null) {
            Oportunidad raiz = raiz(actual);
            de(raiz).ifPresent(lista::add);
            Oportunidad absorbida = raiz.getOportunidadPadre();
            actual = absorbida != null && absorbida.getEstado() == EstadoOportunidad.REEMPLAZADA ? absorbida : null;
        }
        return lista;
    }

    /** Sube por los sobrantes hasta el bloque que se creó al soltar el horario (misma cita de origen). */
    private static Oportunidad raiz(Oportunidad o) {
        Long idCita = o.getCitaOrigen().getIdCita();
        Oportunidad raiz = o;
        while (raiz.getOportunidadPadre() != null
                && raiz.getOportunidadPadre().getCitaOrigen().getIdCita().equals(idCita)
                && raiz.getOportunidadPadre().getEstado() != EstadoOportunidad.REEMPLAZADA) {
            raiz = raiz.getOportunidadPadre();
        }
        return raiz;
    }
}
