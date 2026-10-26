package pe.consultorio.citas.service.disponibilidad;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

/**
Cálculo de horarios libres y de su orden. Java puro, sin Spring ni base de datos: recibe rangos de tiempo y devuelve
rangos y ofertas, por eso se prueba con JUnit sin levantar nada.

Todos los rangos son {@code Range.closedOpen(inicio, fin)}: incluyen el inicio y excluyen el fin. Así, una cita que
termina a las 10:00 no choca con otra que empieza a las 10:00.

Los horarios libres no se guardan en ninguna tabla: se calculan cada vez como
<em>horario del médico − excepciones − citas</em>, y este es el único lugar donde se hace ese cálculo.
*/
public final class MotorDisponibilidad {

    /** Orden de las ofertas: mejor calce primero, luego el que deja menos sobrante, luego el más temprano. */
    private static final Comparator<Oferta> ORDEN = Comparator
        .comparing(Oferta::calce)
        .thenComparingInt(Oferta::residuoMin)
        .thenComparing(Oferta::inicio);

    private MotorDisponibilidad() {
    }

    /**
    Bloques libres = franjas de atención menos lo ocupado.
    
    {@link TreeRangeSet} une solo los rangos que se tocan: dos franjas 08:00–12:00 y 12:00–16:00 dan un único bloque
    08:00–16:00, y al quitar una cita de en medio el bloque se parte en dos.
    
    @param franjas  horario de atención del día (rangos acotados)
    @param ocupados citas, excepciones, retenciones temporales y, si es hoy, el pasado ({@code Range.lessThan(ahora)})
    */
    public static RangeSet<LocalDateTime> bloquesLibres(Collection<Range<LocalDateTime>> franjas,
            Collection<Range<LocalDateTime>> ocupados) {
        RangeSet<LocalDateTime> libres = TreeRangeSet.create();
        franjas.forEach(libres::add);
        ocupados.forEach(libres::remove);
        return libres;
    }

    /**
    Horarios que se pueden ofrecer para una cita de {@code duracionMin} minutos, ya ordenados.
    
    Para cada bloque de L minutos: si L &lt; D no ofrece nada. El sobrante es R = L − D, y su calce es PERFECTO si
    R = 0, VENDIBLE si en R cabe la cita más corta del consultorio ({@code huecoMinimoUtilMin}) y BASURA si no.
    Los inicios van desde el inicio del bloque, cada {@code incrementoMin}, mientras la cita quepa entera.
    
    @param libres             bloques libres (resultado de {@link #bloquesLibres})
    @param duracionMin        duración de la cita que se quiere agendar (D)
    @param incrementoMin      paso entre un inicio y el siguiente
    @param huecoMinimoUtilMin el menor sobrante que todavía sirve para otra cita
    */
    public static List<Oferta> ofertas(RangeSet<LocalDateTime> libres, int duracionMin, int incrementoMin,
            int huecoMinimoUtilMin) {
        if (duracionMin <= 0 || incrementoMin <= 0) {
            throw new IllegalArgumentException("Duración e incremento deben ser mayores que 0");
        }
        List<Oferta> resultado = new ArrayList<>();
        for (Range<LocalDateTime> bloque : libres.asRanges()) {
            if (!bloque.hasLowerBound() || !bloque.hasUpperBound()) {
                continue;   // un bloque sin límite no viene de un horario real; no se ofrece
            }
            LocalDateTime inicioBloque = bloque.lowerEndpoint();
            LocalDateTime finBloque = bloque.upperEndpoint();
            long largo = Duration.between(inicioBloque, finBloque).toMinutes();
            if (largo < duracionMin) {
                continue;
            }
            int residuo = (int) (largo - duracionMin);
            Calce calce = residuo == 0 ? Calce.PERFECTO
                : residuo >= huecoMinimoUtilMin ? Calce.VENDIBLE
                : Calce.BASURA;

            for (LocalDateTime inicio = inicioBloque;
                    !inicio.plusMinutes(duracionMin).isAfter(finBloque);
                    inicio = inicio.plusMinutes(incrementoMin)) {
                resultado.add(new Oferta(inicio, inicio.plusMinutes(duracionMin), inicioBloque, finBloque,
                    residuo, calce, false));
            }
        }
        resultado.sort(ORDEN);
        return resultado;
    }
}
