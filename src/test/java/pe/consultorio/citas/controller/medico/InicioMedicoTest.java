package pe.consultorio.citas.controller.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.TableroMedico;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.AccionAuditoria;
import pe.consultorio.citas.service.AuditoriaService;
import pe.consultorio.citas.service.TableroMedicoService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** El inicio del médico cuenta solo sus citas de hoy y muestra su puntualidad y sus alertas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class InicioMedicoTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired TableroMedicoService tablero;
    @Autowired AuditoriaService auditoria;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaRepository citas;

    private String doc() {
        return String.valueOf(System.nanoTime() % 100000000);
    }

    @Test
    void losContadoresCoincidenConSuAgendaYNoCuentanAOtroMedico() throws Exception {
        Medico m = datos.crearMedico("Inicio", datos.crearEspecialidad("Oftalmología"));
        Medico otro = datos.crearMedico("Vecino", datos.crearEspecialidad("Oftalmología"));
        LocalDate hoy = LocalDate.now();
        Cita atendida = datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay(), 30, EstadoCita.REALIZADA);
        atendida.setInicioReal(atendida.getInicio().plusMinutes(8));
        citas.save(atendida);
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay().plusHours(1), 30, EstadoCita.CONFIRMADA);
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay().plusHours(2), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(m, datos.crearPaciente(doc()), hoy.atStartOfDay().plusHours(3), 30,
            EstadoCita.CANCELADA_POR_PACIENTE);
        datos.crearCita(otro, datos.crearPaciente(doc()), hoy.atStartOfDay().plusHours(1), 30, EstadoCita.CONFIRMADA);
        auditoria.registrar(m.getCuenta().getIdCuenta(), AccionAuditoria.CAMBIO_HORARIO_RECHAZADO, "HORARIO_VERSION",
            null, "Nueva versión desde 01/12: 3 citas quedarían fuera del horario", null);

        TableroMedico t = tablero.tablero(m.getIdMedico(), m.getCuenta().getIdCuenta());
        assertThat(t.citasHoy()).isEqualTo(3);
        assertThat(t.confirmadas()).isEqualTo(1);
        assertThat(t.atendidas()).isEqualTo(1);
        assertThat(t.pendientes()).isEqualTo(2);
        assertThat(t.retrasoMin()).isEqualTo(8);

        mvc.perform(get("/medico/inicio").with(user(detalles.loadUserByUsername(m.getCuenta().getCorreo()))))
            .andExpect(content().string(containsString("Vas 8 min atrasado")))
            .andExpect(content().string(containsString("3 citas quedarían fuera del horario")));
        mvc.perform(get("/medico/inicio").with(user(detalles.loadUserByUsername(otro.getCuenta().getCorreo()))))
            .andExpect(content().string(not(containsString("3 citas quedarían fuera del horario"))));
    }
}
