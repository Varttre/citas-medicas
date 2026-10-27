package pe.consultorio.citas.controller.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.repository.CuentaRepository;

/** Inicio de sesión real contra la base: destino por rol, acceso denegado y bloqueo por intentos fallidos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class LoginTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    PasswordEncoder encoder;

    private Cuenta pacienteConClave(String correo) {
        Cuenta c = datos.crearCuentaPaciente(correo);
        c.setPasswordHash(encoder.encode("Demo2026!"));
        return cuentas.save(c);
    }

    @Test
    void elPacienteEntraASuInicio() throws Exception {
        pacienteConClave("login1@prueba.pe");
        mvc.perform(formLogin().user("correo", "login1@prueba.pe").password("contrasena", "Demo2026!"))
            .andExpect(redirectedUrl("/paciente/inicio"));
    }

    @Test
    void unPacienteNoEntraAlPortalDelMedico() throws Exception {
        mvc.perform(get("/medico/inicio").with(user("p@prueba.pe").roles("PACIENTE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void cincoIntentosFallidosBloqueanLaCuenta() throws Exception {
        pacienteConClave("login2@prueba.pe");
        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin().user("correo", "login2@prueba.pe").password("contrasena", "mala"))
                .andExpect(redirectedUrl("/login?error"));
        }
        assertThat(cuentas.findByCorreo("login2@prueba.pe").orElseThrow().getBloqueadoHasta()).isNotNull();
        mvc.perform(formLogin().user("correo", "login2@prueba.pe").password("contrasena", "Demo2026!"))
            .andExpect(redirectedUrl("/login?bloqueado"));
    }
}
