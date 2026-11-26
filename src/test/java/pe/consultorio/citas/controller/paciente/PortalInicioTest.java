package pe.consultorio.citas.controller.paciente;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.AvisoForm;
import pe.consultorio.citas.entity.Aviso;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.ParametroConsultorioRepository;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** El inicio, los avisos y el WhatsApp del paciente muestran datos reales. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PortalInicioTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired ConfiguracionService configuracion;
    @Autowired ParametroConsultorioRepository parametros;

    @Test
    void elInicioMuestraSusProximasCitasYLosAvisosActivos() throws Exception {
        Medico m = datos.crearMedico("Portal", datos.crearEspecialidad("Pediatría"));
        Cuenta cuenta = datos.crearCuentaPaciente("portal" + System.nanoTime() + "@prueba.pe");
        datos.crearCita(m, cuenta.getPacientePropio(), LocalDate.now().plusDays(3).atTime(10, 0), 30,
            EstadoCita.PROGRAMADA);
        String titulo = "Campaña de vacunación " + System.nanoTime();
        configuracion.guardarAviso(null, new AvisoForm("https://ejemplo.pe/banner.png", titulo, "Vacunas gratis",
            "https://ejemplo.pe", true), null);
        UsuarioSesion sesion = detalles.loadUserByUsername(cuenta.getCorreo());

        mvc.perform(get("/paciente/inicio").with(user(sesion)))
            .andExpect(content().string(containsString("Portal Prueba")))
            .andExpect(content().string(containsString(titulo)));
        mvc.perform(get("/paciente/avisos").with(user(sesion)))
            .andExpect(content().string(containsString(titulo)))
            .andExpect(content().string(containsString("rel=\"noopener\"")));
    }

    /** Pedido del docente: los avisos van plegados en el inicio y desde ahí se entra a cada uno. */
    @Test
    void losAvisosVanPlegadosEnElInicioYCadaUnoTieneSuDetalle() throws Exception {
        Cuenta cuenta = datos.crearCuentaPaciente("plegado" + System.nanoTime() + "@prueba.pe");
        UsuarioSesion sesion = detalles.loadUserByUsername(cuenta.getCorreo());
        String sufijo = String.valueOf(System.nanoTime());
        Aviso activo = configuracion.guardarAviso(null, new AvisoForm("https://ejemplo.pe/b.png", "Activo " + sufijo,
            "Texto completo del aviso " + sufijo, "/paciente/cita/nueva", true), null);
        Aviso inactivo = configuracion.guardarAviso(null, new AvisoForm("https://ejemplo.pe/b.png", "Inactivo " + sufijo,
            "No se publica", "https://ejemplo.pe", false), null);

        mvc.perform(get("/paciente/inicio").with(user(sesion)))
            .andExpect(content().string(containsString("data-bs-toggle=\"dropdown\"")))
            .andExpect(content().string(containsString("/paciente/avisos/" + activo.getIdAviso())))
            .andExpect(content().string(not(containsString("Inactivo " + sufijo))))
            .andExpect(content().string(not(containsString("rounded-pill text-bg-danger"))))
            .andExpect(content().string(containsString("https://medlineplus.gov/spanish/")));
        mvc.perform(get("/paciente/avisos/" + activo.getIdAviso()).with(user(sesion)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Texto completo del aviso " + sufijo)))
            .andExpect(content().string(containsString("href=\"/paciente/cita/nueva\"")));
        mvc.perform(get("/paciente/avisos/" + inactivo.getIdAviso()).with(user(sesion)))
            .andExpect(status().isNotFound());
    }

    @Test
    void elEnlaceDeWhatsappLlevaElNombreYElDocumentoCodificados() throws Exception {
        Cuenta cuenta = datos.crearCuentaPaciente("wsp" + System.nanoTime() + "@prueba.pe");
        Paciente p = cuenta.getPacientePropio();
        UsuarioSesion sesion = detalles.loadUserByUsername(cuenta.getCorreo());
        ParametroConsultorio fila = configuracion.parametros();
        String original = fila.getWhatsappNumero();
        try {
            mvc.perform(get("/paciente/whatsapp").with(user(sesion)))
                .andExpect(content().string(containsString("no tiene un número de WhatsApp")));
            fila.setWhatsappNumero("+51 987 654 321");
            parametros.save(fila);
            mvc.perform(get("/paciente/whatsapp").with(user(sesion)))
                .andExpect(content().string(containsString("https://wa.me/51987654321?text=Hola%2C%20soy%20Paciente%20"
                    + p.getApellidos() + "%20%28DNI%20" + p.getNroDocumento() + "%29.")))
                .andExpect(content().string(containsString("Deseo%20contactar%20con%20un%20agente.")));
        } finally {
            ParametroConsultorio r = configuracion.parametros();
            r.setWhatsappNumero(original);
            parametros.save(r);
        }
    }
}
