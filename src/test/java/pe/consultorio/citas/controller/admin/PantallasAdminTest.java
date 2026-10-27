package pe.consultorio.citas.controller.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.PostgresTestConfig;

/** Las pantallas de administración cargan para el administrador y el superadministrador; recepción no entra. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PantallasAdminTest {

    @Autowired
    MockMvc mvc;

    @Test
    void administradorYSuperadministradorVenTodas() throws Exception {
        for (String ruta : new String[] {"/admin/configuracion", "/admin/usuarios", "/admin/reportes"}) {
            mvc.perform(get(ruta).with(user("a@prueba.pe").roles("ADMINISTRADOR"))).andExpect(status().isOk());
            mvc.perform(get(ruta).with(user("s@prueba.pe").roles("SUPERADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void recepcionNoEntra() throws Exception {
        mvc.perform(get("/admin/usuarios").with(user("r@prueba.pe").roles("RECEPCION")))
            .andExpect(status().isForbidden());
    }
}
