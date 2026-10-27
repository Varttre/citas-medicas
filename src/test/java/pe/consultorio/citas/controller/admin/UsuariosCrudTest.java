package pe.consultorio.citas.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.entity.EstadoCuenta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.MedicoRepository;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** Alta, bloqueo y baja de usuarios del personal contra la base real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class UsuariosCrudTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    UsuarioDetallesService detalles;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    MedicoRepository medicos;

    @Autowired
    ConfiguracionService configuracion;

    UsuarioSesion admin;

    @BeforeEach
    void preparar() {
        String correo = "admin" + System.nanoTime() + "@prueba.pe";
        usuarioService.crearPersonal(correo, "Clave2026!", NombreRol.ADMINISTRADOR, "Admin de prueba", null);
        admin = detalles.loadUserByUsername(correo);
    }

    @Test
    void creaPersonalYLoBloquea() throws Exception {
        mvc.perform(post("/admin/usuarios/personal").with(user(admin)).with(csrf())
                .param("correo", "ana.recepcion@prueba.pe").param("contrasena", "Clave2026!")
                .param("rol", "RECEPCION").param("nombreVisible", "Ana Quispe"))
            .andExpect(flash().attribute("exito", "Usuario creado."));
        mvc.perform(get("/admin/usuarios").param("q", "quispe").with(user(admin)))
            .andExpect(content().string(containsString("Ana Quispe")));

        Long id = cuentas.findByCorreo("ana.recepcion@prueba.pe").orElseThrow().getIdCuenta();
        mvc.perform(post("/admin/usuarios/" + id + "/bloquear").with(user(admin)).with(csrf()));
        assertThat(cuentas.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoCuenta.BLOQUEADA);
    }

    @Test
    void creaUnMedicoConSusDuraciones() throws Exception {
        var alta = post("/admin/usuarios/medicos").with(user(admin)).with(csrf())
            .param("correo", "patricia.nunez@prueba.pe").param("contrasena", "Clave2026!")
            .param("nombres", "Patricia").param("apellidos", "Núñez")
            .param("idEspecialidad", configuracion.especialidadesActivas().get(0).getIdEspecialidad().toString());
        for (TipoConsulta t : configuracion.tiposActivos()) {
            alta.param("dur_" + t.getIdTipoConsulta(), String.valueOf(t.getDuracionDefecto()));
        }
        mvc.perform(alta).andExpect(flash().attributeExists("exito"));
        Long idCuenta = cuentas.findByCorreo("patricia.nunez@prueba.pe").orElseThrow().getIdCuenta();
        assertThat(medicos.findByCuentaIdCuenta(idCuenta)).isPresent();
    }

    @Test
    void noPermiteCorreosRepetidos() throws Exception {
        mvc.perform(post("/admin/usuarios/personal").with(user(admin)).with(csrf())
                .param("correo", admin.getUsername()).param("contrasena", "Clave2026!")
                .param("rol", "RECEPCION").param("nombreVisible", "Otra"))
            .andExpect(flash().attribute("error", "Ya existe una cuenta con ese correo."));
    }
}
