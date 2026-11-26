package pe.consultorio.citas.controller.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.CitaAfectada;
import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.exception.PlazoVencidoException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.HorarioService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/**
Ajustes pedidos al revisar el bloque 4: el médico cambia su horario desde hoy (ampliar siempre se puede; reducir solo si
no deja citas fuera), los estados olvidados se registran después del día (registro tardío) y los filtros de médico y
especialidad llevan lo necesario para filtrarse entre sí.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class HorarioYRegistroTardioTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired HorarioService horarios;
    @Autowired CitaEstadoService estados;
    @Autowired CitaRepository citas;
    @Autowired CitaEventoRepository eventos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;

    Medico medico;
    UsuarioSesion sesion;

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Horario", datos.crearEspecialidad("Cambia horario"));
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(10), d, LocalTime.of(9, 0), LocalTime.of(12, 0));
        }
        sesion = detalles.loadUserByUsername(medico.getCuenta().getCorreo());
    }

    private MockHttpServletRequestBuilder cambiarHorario(LocalDate desde, String inicio, String fin) {
        MockHttpServletRequestBuilder req = post("/medico/horario/versiones").with(user(sesion)).with(csrf())
            .param("desde", desde.toString());
        for (int d = 1; d <= 7; d++) {
            req.param("dia_" + d, "1").param("ini_" + d + "_1", inicio).param("fin_" + d + "_1", fin);
        }
        return req;
    }

    private Cita cita(LocalDateTime inicio) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), inicio, 30,
            EstadoCita.PROGRAMADA);
    }

    /** PA-164 ★ */
    @Test
    void elMedicoAmpliaSuHorarioDesdeHoyAunqueTengaCitasYNoPuedeDejarlasFuera() throws Exception {
        Cita manana = cita(LocalDate.now().plusDays(1).atTime(11, 0));
        mvc.perform(get("/medico/horario").with(user(sesion)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Cambiar mi horario")))
            .andExpect(content().string(containsString("value=\"09:00\"")));

        // Ampliar hasta las 22:00 desde hoy: la cita de mañana sigue cabiendo.
        mvc.perform(cambiarHorario(LocalDate.now(), "08:00", "22:00"))
            .andExpect(flash().attribute("exito", containsString("Horario actualizado")));
        var vigente = horarios.verHorario(medico.getIdMedico()).versiones().stream()
            .filter(v -> "VIGENTE".equals(v.estado())).findFirst().orElseThrow();
        assertThat(vigente.vigenteDesde()).isEqualTo(LocalDate.now());
        assertThat(vigente.franjas()).contains(new FranjaDatos(1, LocalTime.of(8, 0), LocalTime.of(22, 0)));

        // Reducirlo a 9:00–10:00 dejaría fuera la cita de las 11:00: se rechaza, la lista y conserva lo enviado.
        var r = mvc.perform(cambiarHorario(LocalDate.now(), "09:00", "10:00"))
            .andExpect(flash().attribute("errorHorario", containsString("deja fuera 1 cita")))
            .andReturn();
        @SuppressWarnings("unchecked")
        List<CitaAfectada> afectadas = (List<CitaAfectada>) r.getFlashMap().get("afectadas");
        assertThat(afectadas).singleElement().satisfies(a -> assertThat(a.idCita()).isEqualTo(manana.getIdCita()));
        mvc.perform(get("/medico/horario").with(user(sesion)).cookie(r.getResponse().getCookies()))
            .andExpect(content().string(containsString("value=\"10:00\"")))
            .andExpect(content().string(containsString("Tu propuesta se conserva")));
        // Sigue el horario ampliado.
        assertThat(horarios.verHorario(medico.getIdMedico()).versiones().stream()
            .filter(v -> "VIGENTE".equals(v.estado())).findFirst().orElseThrow().franjas())
            .contains(new FranjaDatos(1, LocalTime.of(8, 0), LocalTime.of(22, 0)));
    }

    /** PA-165 ★ */
    @Test
    void unEstadoOlvidadoSeRegistraOtroDiaComoRegistroTardio() {
        Long recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION).getIdCuenta();
        LocalDateTime haceTresDias = LocalDate.now().minusDays(3).atTime(9, 0);
        Cita olvidada = cita(haceTresDias);
        Cita atendida = cita(haceTresDias.plusMinutes(30));

        estados.marcarNoAsistio(olvidada.getIdCita(), recepcion);
        assertThat(citas.findById(olvidada.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.NO_ASISTIO);
        assertThat(ultimoMotivo(olvidada)).isEqualTo("Registro tardío");

        // Llegada y atención registradas días después: se guardan las horas de la cita, no las de hoy.
        estados.marcarLlegada(atendida.getIdCita(), recepcion);
        estados.iniciarAtencion(atendida.getIdCita(), medico.getCuenta().getIdCuenta());
        estados.finalizarAtencion(atendida.getIdCita(), medico.getCuenta().getIdCuenta());
        Cita r = citas.findById(atendida.getIdCita()).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoCita.REALIZADA);
        assertThat(r.getHoraLlegada()).isEqualTo(r.getInicio());
        assertThat(r.getInicioReal()).isEqualTo(r.getInicio());
        assertThat(r.getFinReal()).isEqualTo(r.getFin());

        // El inicio del plazo se sigue exigiendo: no se registra la llegada de una cita de dentro de dos días.
        Cita futura = cita(LocalDate.now().plusDays(2).atTime(10, 0));
        assertThatThrownBy(() -> estados.marcarLlegada(futura.getIdCita(), recepcion))
            .isInstanceOf(PlazoVencidoException.class).hasMessageContaining("Aún no se puede");
    }

    private String ultimoMotivo(Cita c) {
        List<CitaEvento> lista = eventos.findByCitaIdCitaAndFechaHoraLessThanEqualOrderByFechaHoraDesc(c.getIdCita(),
            LocalDateTime.now().plusMinutes(1));
        return lista.stream().filter(e -> e.getEstadoNuevo() != null).findFirst().orElseThrow().getMotivo();
    }

    /** PA-166 */
    @Test
    void losFiltrosDeMedicoLlevanSusEspecialidadesParaFiltrarseEntreSi() throws Exception {
        Especialidad otra = datos.crearEspecialidad("Segunda");
        datos.agregarEspecialidad(medico, otra);
        String a = "adminf" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(a, "Clave2026!", NombreRol.ADMINISTRADOR, "Admin filtros", null);
        String r = "recepf" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(r, "Clave2026!", NombreRol.RECEPCION, "Recepción filtros", null);

        for (var caso : List.of(List.of(a, "/admin/reportes"), List.of(r, "/recepcion/agenda"))) {
            String html = mvc.perform(get(caso.get(1)).with(user(detalles.loadUserByUsername(caso.get(0)))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-filtro-especialidad=")))
                .andReturn().getResponse().getContentAsString();
            // El médico aparece una sola vez, con sus dos especialidades.
            // (el mismo número puede ser el id de una especialidad: solo cuentan las opciones de médico, con data-esp)
            java.util.regex.Matcher etiqueta = java.util.regex.Pattern
                .compile("<option value=\"" + medico.getIdMedico() + "\"[^>]*data-esp=\"([0-9 ]+)\"[^>]*>")
                .matcher(html);
            assertThat(etiqueta.find()).isTrue();
            assertThat(etiqueta.group(1).split(" "))
                .containsExactlyInAnyOrder(String.valueOf(datos.especialidadPrincipal(medico)),
                    String.valueOf(otra.getIdEspecialidad()));
            assertThat(etiqueta.find()).isFalse();
        }
    }
}
