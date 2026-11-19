package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.TableroRecepcion;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;

/** Los contadores del inicio de recepción cambian con los datos y coinciden con sus pantallas de destino. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class TableroRecepcionTest {

    @Autowired TableroRecepcionService tablero;
    @Autowired ColaService colas;
    @Autowired OportunidadService oportunidades;
    @Autowired DatosPrueba datos;

    private String doc() {
        return String.valueOf(System.nanoTime() % 100000000);
    }

    @Test
    void cadaContadorSubeConSuCasoYCoincideConSuPantalla() {
        TableroRecepcion antes = tablero.tablero();
        Medico m = datos.crearMedico("Tablero", datos.crearEspecialidad("Urología"));
        LocalDate hoy = LocalDate.now();
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay(), 15, EstadoCita.ASISTIO);
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay().plusMinutes(15), 15, EstadoCita.PROGRAMADA);
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.minusDays(1).atTime(10, 0), 30, EstadoCita.CONFIRMADA);

        TableroRecepcion despues = tablero.tablero();
        assertThat(despues.enSala()).isEqualTo(antes.enSala() + 1);
        assertThat(despues.hoySinLlegada()).isEqualTo(antes.hoySinLlegada() + 1);
        assertThat(despues.ayerSinEstado()).isEqualTo(antes.ayerSinEstado() + 1);
        assertThat(despues.porConfirmarHoy()).isEqualTo(colas.confirmaciones(hoy, null, null).size());
        assertThat(despues.cupos()).isEqualTo(oportunidades.abiertas().size());
        assertThat(despues.medicos()).anySatisfy(x -> {
            assertThat(x.medico()).isEqualTo("Tablero Prueba");
            assertThat(x.estado()).startsWith("Retrasado");
        });
    }
}
