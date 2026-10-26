package pe.consultorio.citas.service.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import org.junit.jupiter.api.Test;

/** Casos del motor con un día fijo (miércoles 2026-11-11). JUnit puro, sin Spring. */
class MotorDisponibilidadTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 11);

    private static LocalDateTime h(int hora, int minuto) {
        return DIA.atTime(hora, minuto);
    }

    private static Range<LocalDateTime> r(int h1, int m1, int h2, int m2) {
        return Range.closedOpen(h(h1, m1), h(h2, m2));
    }

    private static List<LocalTime> inicios(List<Oferta> ofertas) {
        return ofertas.stream().map(o -> o.inicio().toLocalTime()).toList();
    }

    @Test
    void caso1_quitarLasCitasDejaUnSoloBloqueEntreEllas() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(
            List.of(r(8, 0, 16, 0)), List.of(r(8, 0, 11, 30), r(14, 0, 16, 0)));

        assertThat(libres.asRanges()).containsExactly(r(11, 30, 14, 0));
    }

    @Test
    void caso2_dosFranjasQueSeTocanSeUnenEnUnBloque() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(
            List.of(r(8, 0, 12, 0), r(12, 0, 16, 0)), List.of());

        assertThat(libres.asRanges()).containsExactly(r(8, 0, 16, 0));
    }

    @Test
    void caso3_unaCitaEnMedioParteElBloqueEnDos() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(
            List.of(r(8, 0, 13, 0)), List.of(r(10, 0, 11, 0)));

        assertThat(libres.asRanges()).containsExactly(r(8, 0, 10, 0), r(11, 0, 13, 0));
    }

    @Test
    void caso4_losIniciosAvanzanPorIncrementoMientrasLaCitaQuepa() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(List.of(r(9, 0, 10, 0)), List.of());

        List<Oferta> ofertas = MotorDisponibilidad.ofertas(libres, 30, 15, 30);

        // 09:45 no: terminaría a las 10:15
        assertThat(inicios(ofertas)).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30));
    }

    @Test
    void caso5_ordenPorCalceLuegoResiduoLuegoHora() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(
            List.of(r(9, 0, 9, 30), r(10, 0, 11, 0), r(12, 0, 12, 45)), List.of());

        List<Oferta> ofertas = MotorDisponibilidad.ofertas(libres, 30, 15, 30);

        assertThat(ofertas)
            .extracting(o -> o.inicio().toLocalTime(), Oferta::calce, Oferta::residuoMin)
            .containsExactly(
                tuple(LocalTime.of(9, 0), Calce.PERFECTO, 0),
                tuple(LocalTime.of(10, 0), Calce.VENDIBLE, 30),
                tuple(LocalTime.of(10, 15), Calce.VENDIBLE, 30),
                tuple(LocalTime.of(10, 30), Calce.VENDIBLE, 30),
                tuple(LocalTime.of(12, 0), Calce.BASURA, 15),
                tuple(LocalTime.of(12, 15), Calce.BASURA, 15));
    }

    @Test
    void caso6_unBloqueMasCortoQueLaCitaNoOfreceNada() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(List.of(r(9, 0, 9, 20)), List.of());

        assertThat(MotorDisponibilidad.ofertas(libres, 30, 15, 30)).isEmpty();
    }

    @Test
    void cadaOfertaRecuerdaSuBloqueYNoEstaProtegida() {
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(List.of(r(10, 0, 11, 0)), List.of());

        Oferta primera = MotorDisponibilidad.ofertas(libres, 30, 15, 30).get(0);

        assertThat(primera.fin()).isEqualTo(h(10, 30));
        assertThat(primera.bloqueInicio()).isEqualTo(h(10, 0));
        assertThat(primera.bloqueFin()).isEqualTo(h(11, 0));
        assertThat(primera.protegida()).isFalse();
    }

    @Test
    void elPasadoSeQuitaComoUnRangoSinInicio() {
        // Hoy a las 10:07: se quita todo lo anterior a 10:15 (redondeado a la rejilla)
        LocalDateTime corte = Rejilla.redondearArriba(h(10, 7), 15);
        RangeSet<LocalDateTime> libres = MotorDisponibilidad.bloquesLibres(
            List.of(r(9, 0, 12, 0)), List.of(Range.lessThan(corte)));

        assertThat(libres.asRanges()).containsExactly(r(10, 15, 12, 0));
    }
}
