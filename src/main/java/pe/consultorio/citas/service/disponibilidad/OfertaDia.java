package pe.consultorio.citas.service.disponibilidad;

import java.time.LocalDate;
import java.util.List;

/** Horarios ofrecidos de un día, ya ordenados. */
public record OfertaDia(LocalDate fecha, List<Oferta> ofertas) {
}
