package pe.consultorio.citas.controller.recepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** Recepción recorre la cola de cupos: llama, posterga y, cuando un candidato acepta, su cita se adelanta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CuposFlujoTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaEstadoService estados;
    @Autowired PreferenciaService preferencias;
    @Autowired CitaRepository citas;
    @Autowired OportunidadRepository oportunidades;

    Medico medico;
    UsuarioSesion recepcion;
    LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Cupos", datos.crearEspecialidad("Reumatología"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
        String correo = "recepcupos" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción", null);
        recepcion = detalles.loadUserByUsername(correo);
    }

    private Cita cita(LocalDate dia, int h, int m) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            dia.atTime(h, m), 30, EstadoCita.PROGRAMADA);
    }

    @Test
    void unCandidatoAceptaYSuCitaSeAdelantaConservandoElId() throws Exception {
        Cita cancelada = cita(manana, 9, 0);
        cita(manana, 9, 30);
        estados.cancelarPorClinica(cancelada.getIdCita(), null, "El paciente avisó tarde");
        Oportunidad op = oportunidades.findAll().stream()
            .filter(o -> o.getMedico().getIdMedico().equals(medico.getIdMedico())
                && o.getEstado() == EstadoOportunidad.ABIERTA).findFirst().orElseThrow();
        Paciente pacienteLejano = datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000));
        Cita lejana = datos.crearCita(medico, pacienteLejano, manana.plusDays(3).atTime(9, 0), 30,
            EstadoCita.PROGRAMADA);
        preferencias.guardar(lejana.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 5), null);
        String base = "/recepcion/cupos/" + op.getIdOportunidad();

        mvc.perform(get(base).with(user(recepcion)))
            .andExpect(content().string(containsString("Cupos Prueba")))
            .andExpect(content().string(containsString(pacienteLejano.getApellidos())))
            .andExpect(content().string(containsString("Exacto")));

        mvc.perform(post(base + "/candidatos/" + lejana.getIdCita() + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "NO_CONTESTO").param("posicion", "1")
                .param("inicioPropuesto", manana + "T09:00"))
            .andExpect(redirectedUrl(base));
        mvc.perform(post(base + "/candidatos/" + lejana.getIdCita() + "/postergar").with(user(recepcion)).with(csrf()))
            .andExpect(flash().attribute("aviso", "Ese candidato ya es el último de la lista."));

        mvc.perform(post(base + "/candidatos/" + lejana.getIdCita() + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "ACEPTO").param("posicion", "1").param("inicioPropuesto", manana + "T09:00"))
            .andExpect(redirectedUrl("/recepcion/cupos"))
            .andExpect(flash().attribute("exito", containsString("Cupo cubierto")));

        assertThat(citas.findById(lejana.getIdCita()).orElseThrow().getInicio()).isEqualTo(manana.atTime(9, 0));
        assertThat(oportunidades.findById(op.getIdOportunidad()).orElseThrow().getEstado())
            .isEqualTo(EstadoOportunidad.CUBIERTA);
        mvc.perform(get("/recepcion/cupos").with(user(recepcion)))
            .andExpect(content().string(not(containsString(base + "\""))));
    }

    @Test
    void unResultadoInvalidoNoAdelantaNada() throws Exception {
        Cita cancelada = cita(manana, 9, 0);
        estados.cancelarPorClinica(cancelada.getIdCita(), null, "Prueba");
        Oportunidad op = oportunidades.findAll().stream()
            .filter(o -> o.getMedico().getIdMedico().equals(medico.getIdMedico())
                && o.getEstado() == EstadoOportunidad.ABIERTA).findFirst().orElseThrow();
        Cita otra = cita(manana.plusDays(2), 9, 30);
        mvc.perform(post("/recepcion/cupos/" + op.getIdOportunidad() + "/candidatos/" + otra.getIdCita() + "/resultado")
                .with(user(recepcion)).with(csrf()).param("resultado", "CONFIRMO").param("posicion", "1")
                .param("inicioPropuesto", manana + "T09:00"))
            .andExpect(flash().attribute("error", containsString("cupos liberados")));
        assertThat(citas.findById(otra.getIdCita()).orElseThrow().getInicio()).isEqualTo(manana.plusDays(2).atTime(9, 30));
    }
}
