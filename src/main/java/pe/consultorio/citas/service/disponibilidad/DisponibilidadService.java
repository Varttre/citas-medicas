package pe.consultorio.citas.service.disponibilidad;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.google.common.collect.RangeSet;
import org.springframework.stereotype.Service;

/** Calcula los horarios libres de un médico a partir de su horario, sus excepciones y sus citas. */
@Service
public class DisponibilidadService {

    /**
    Los tres primeros días, desde {@code desde}, que tengan al menos un horario libre para una cita de
    {@code duracionMin} minutos. No ofrece horarios retenidos por otra cuenta.
    */
    public List<OfertaDia> ofertasProximas(Long idMedico, int duracionMin, LocalDate desde, Long idCuentaQuePide) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }

    /** Horarios libres de un día para una cita de {@code duracionMin} minutos, ya ordenados. */
    public List<Oferta> ofertasDelDia(Long idMedico, int duracionMin, LocalDate fecha, Long idCuentaQuePide) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }

    /** Bloques libres del día, sin descontar retenciones temporales ni el pasado. */
    public RangeSet<LocalDateTime> bloquesLibresDelDia(Long idMedico, LocalDate fecha) {
        throw new UnsupportedOperationException("Pendiente: A4");
    }
}
