package pe.consultorio.citas.controller.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** «Mi perfil» del paciente y la cancelación de sus citas desde «Mis citas». */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PerfilTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    CitaRepository citas;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    UsuarioDetallesService detalles;

    private UsuarioSesion paciente(String correo) {
        Cuenta c = datos.crearCuentaPaciente(correo);
        c.setPasswordHash(encoder.encode("Demo2026!"));
        cuentas.save(c);
        return detalles.loadUserByUsername(correo);
    }

    @Test
    void cambiaElTelefonoYLaContrasenaSoloConLaActualCorrecta() throws Exception {
        UsuarioSesion p = paciente("perfil" + System.nanoTime() + "@prueba.pe");
        mvc.perform(get("/paciente/perfil").with(user(p))).andExpect(status().isOk());
        mvc.perform(post("/paciente/perfil/datos").with(user(p)).with(csrf())
                .param("telefono", "955444333").param("correo", p.getUsername()))
            .andExpect(flash().attribute("exito", "Datos actualizados."));
        mvc.perform(get("/paciente/perfil").with(user(p))).andExpect(content().string(containsString("955444333")));

        mvc.perform(post("/paciente/perfil/contrasena").with(user(p)).with(csrf())
                .param("actual", "equivocada").param("nueva", "Nueva2026!").param("confirmacion", "Nueva2026!"))
            .andExpect(flash().attribute("error", "La contraseña actual no es correcta."));
        mvc.perform(post("/paciente/perfil/contrasena").with(user(p)).with(csrf())
                .param("actual", "Demo2026!").param("nueva", "Nueva2026!").param("confirmacion", "Nueva2026!"))
            .andExpect(redirectedUrl("/login?contrasenaCambiada"));
        assertThat(encoder.matches("Nueva2026!", cuentas.findByCorreo(p.getUsername()).orElseThrow().getPasswordHash()))
            .isTrue();
    }

    @Test
    void cancelaSuCitaPeroNoLaDeOtro() throws Exception {
        UsuarioSesion p = paciente("cancela" + System.nanoTime() + "@prueba.pe");
        Medico m = datos.crearMedico("Perfil", datos.crearEspecialidad("Reumatología"));
        Cita mia = datos.crearCita(m, datos.crearPaciente("85000001"), LocalDate.now().plusDays(5).atTime(9, 0), 30,
            EstadoCita.PROGRAMADA);
        // la cita de prueba es de otro paciente: se la asignamos al paciente propio de la cuenta
        Cuenta c = cuentas.findByCorreo(p.getUsername()).orElseThrow();
        mia.setPaciente(c.getPacientePropio());
        citas.save(mia);
        Cita ajena = datos.crearCita(m, datos.crearPaciente("85000002"), LocalDate.now().plusDays(5).atTime(10, 0), 30,
            EstadoCita.PROGRAMADA);

        mvc.perform(post("/paciente/citas/" + ajena.getIdCita() + "/cancelar").with(user(p)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/paciente/citas/" + mia.getIdCita() + "/cancelar").with(user(p)).with(csrf()))
            .andExpect(redirectedUrl("/paciente/citas"));
        assertThat(citas.findById(mia.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CANCELADA_POR_PACIENTE);
    }
}
