package pe.consultorio.citas.controller.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.EstadisticasMedico;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.EstadisticaMedicoService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** Las estadísticas del médico salen de sus citas reales y nunca de las de otro médico. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class EstadisticasMedicoTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired EstadisticaMedicoService estadisticas;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;

    private Cita realizada(Medico m, LocalDateTime inicio, int retraso, int duracionReal) {
        Cita c = datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), inicio, 30,
            EstadoCita.REALIZADA);
        c.setInicioReal(inicio.plusMinutes(retraso));
        c.setFinReal(inicio.plusMinutes(retraso + duracionReal));
        return citas.save(c);
    }

    @Test
    void duracionesPuntualidadYAusenciasDeSusCitas() throws Exception {
        Medico m = datos.crearMedico("Estadística", datos.crearEspecialidad("Cardiología"));
        Medico otro = datos.crearMedico("Ajeno", datos.crearEspecialidad("Cardiología"));
        LocalDate ayer = LocalDate.now().minusDays(1);
        realizada(m, ayer.atTime(8, 0), 0, 20);
        realizada(m, ayer.atTime(9, 0), 10, 30);
        realizada(m, ayer.atTime(10, 0), 5, 50);
        datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), ayer.atTime(11, 0), 30,
            EstadoCita.NO_ASISTIO);
        realizada(otro, ayer.atTime(8, 0), 60, 90);

        EstadisticasMedico e = estadisticas.calcular(m.getIdMedico());
        EstadisticasMedico.DuracionTipo tipo = e.duraciones().stream().filter(d -> d.citas() == 3).findFirst()
            .orElseThrow();
        assertThat(tipo.mediana()).isEqualTo(30.0);
        assertThat(tipo.p75()).isEqualTo(50);
        assertThat(tipo.variable()).isTrue();
        assertThat(e.retrasoPromedioMin()).isEqualTo(5.0);
        assertThat(e.iniciadasTardePct()).isEqualTo(67);
        assertThat(e.noAsistenciaPct()).isEqualTo(25);
        assertThat(e.minutosPerdidos()).isEqualTo(30);

        mvc.perform(get("/medico/estadisticas").with(user(detalles.loadUserByUsername(m.getCuenta().getCorreo()))))
            .andExpect(content().string(containsString("duraciones muy variables")))
            .andExpect(content().string(containsString("25 %")));
    }
}
