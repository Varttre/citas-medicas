package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Mediana y percentil por rango más cercano, calculados a mano. */
class EstadisticaTest {

    @Test
    void medianaConCantidadImparYPar() {
        assertThat(Estadistica.mediana(List.of(40, 20, 30))).isEqualTo(30.0);
        assertThat(Estadistica.mediana(List.of(20, 30))).isEqualTo(25.0);
        assertThat(Estadistica.mediana(List.of())).isNull();
    }

    @Test
    void percentil75PorRangoMasCercanoYRanura() {
        assertThat(Estadistica.percentil(List.of(10, 20, 30, 40), 75)).isEqualTo(30);
        assertThat(Estadistica.ranura(38, 15)).isEqualTo(45);
        assertThat(Estadistica.ranura(30, 15)).isEqualTo(30);
    }
}
