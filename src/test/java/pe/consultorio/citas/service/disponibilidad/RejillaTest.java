package pe.consultorio.citas.service.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Puntos de la rejilla de tiempo. JUnit puro. */
class RejillaTest {

    private static LocalDateTime t(int hora, int minuto, int segundo) {
        return LocalDateTime.of(2026, 11, 11, hora, minuto, segundo);
    }

    @Test
    void redondearArribaLlevaAlSiguientePuntoYRespetaLosQueYaEstan() {
        assertThat(Rejilla.redondearArriba(t(10, 7, 0), 15)).isEqualTo(t(10, 15, 0));
        assertThat(Rejilla.redondearArriba(t(10, 15, 0), 15)).isEqualTo(t(10, 15, 0));
        assertThat(Rejilla.redondearArriba(t(10, 15, 30), 15)).isEqualTo(t(10, 30, 0));
    }

    @Test
    void redondearArribaCercaDeMedianochePasaAlDiaSiguiente() {
        assertThat(Rejilla.redondearArriba(t(23, 50, 0), 15)).isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 0));
    }

    @Test
    void enRejillaExigeMinutosMultiplosYSinSegundos() {
        assertThat(Rejilla.enRejilla(t(14, 15, 0), 15)).isTrue();
        assertThat(Rejilla.enRejilla(t(14, 13, 0), 15)).isFalse();
        assertThat(Rejilla.enRejilla(t(14, 15, 1), 15)).isFalse();
        assertThat(Rejilla.enRejilla(t(14, 30, 0), 30)).isTrue();
        assertThat(Rejilla.enRejilla(t(14, 45, 0), 30)).isFalse();
    }

    @Test
    void horasDelDiaIncluyeLosDosExtremos() {
        assertThat(Rejilla.horasDelDia(LocalTime.of(8, 0), LocalTime.of(9, 0), 15)).containsExactly(
            LocalTime.of(8, 0), LocalTime.of(8, 15), LocalTime.of(8, 30), LocalTime.of(8, 45), LocalTime.of(9, 0));
    }

    @Test
    void horasDelDiaDeSeisAVeintidosTieneSesentaYCincoOpciones() {
        assertThat(Rejilla.horasDelDia(LocalTime.of(6, 0), LocalTime.of(22, 0), 15)).hasSize(65);
    }

    @Test
    void horasDelDiaNoDaLaVueltaALaMedianoche() {
        assertThat(Rejilla.horasDelDia(LocalTime.of(23, 30), LocalTime.of(23, 59), 15))
            .containsExactly(LocalTime.of(23, 30), LocalTime.of(23, 45));
    }

    @Test
    void unIncrementoCeroEsUnError() {
        assertThatThrownBy(() -> Rejilla.enRejilla(t(10, 0, 0), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
