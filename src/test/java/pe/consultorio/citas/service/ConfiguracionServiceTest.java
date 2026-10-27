package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.PoliticaProteccion;
import pe.consultorio.citas.entity.Transicion;

/** Los datos iniciales (V2) quedan cargados y se leen por el servicio de configuración. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class ConfiguracionServiceTest {

    @Autowired
    ConfiguracionService configuracion;

    @Test
    void parametrosYCatalogosIniciales() {
        assertThat(configuracion.parametros().getPoliticaProteccion()).isEqualTo(PoliticaProteccion.NUNCA);
        assertThat(configuracion.incremento()).isEqualTo(15);
        assertThat(configuracion.huecoMinimoUtil()).isEqualTo(30);
        assertThat(configuracion.tiposWeb()).extracting("nombre").contains("Consulta");
        assertThat(configuracion.especialidadesActivas()).extracting("nombre")
            .contains("Cardiología", "Medicina General", "Pediatría");
        assertThat(configuracion.plazo(Transicion.ASISTIO).getMinutosDesde()).isEqualTo((short) -30);
    }
}
