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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
import pe.consultorio.citas.dto.Candidato;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.CandidatoService;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/**
Ajustes de la cola de cupos tras la revisión del docente: la hora que se muestra, el adelanto con otro médico de la
especialidad, el cupo contiguo que debe quedar cubierto, «Mover al final» y la búsqueda de pacientes.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CuposAjustesTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;
    @Autowired CitaEstadoService estados;
    @Autowired PreferenciaService preferencias;
    @Autowired CandidatoService candidatos;
    @Autowired CitaRepository citas;
    @Autowired OportunidadRepository oportunidades;

    Especialidad especialidad;
    Medico medico;
    UsuarioSesion recepcion;
    LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Dermatología");
        medico = conHorario(datos.crearMedico("Ajustes", especialidad), LocalTime.of(8, 0), LocalTime.of(11, 0));
        String correo = "recepajustes" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción", null);
        recepcion = detalles.loadUserByUsername(correo);
    }

    private Medico conHorario(Medico m, LocalTime desde, LocalTime hasta) {
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(m, LocalDate.now().minusDays(1), d, desde, hasta);
        }
        return m;
    }

    private Paciente paciente() {
        return datos.crearPaciente(String.valueOf(10000000 + System.nanoTime() % 89999999));
    }

    private Oportunidad abiertaDe(Medico m) {
        return oportunidades.findAll().stream()
            .filter(o -> o.getMedico().getIdMedico().equals(m.getIdMedico()) && o.getEstado() == EstadoOportunidad.ABIERTA)
            .findFirst().orElseThrow();
    }

    @Test
    void elCupoMuestraLaHoraQueSeLiberoYAparteElBloqueLibre() throws Exception {
        Cita carmen = datos.crearCita(medico, paciente(), manana.atTime(8, 15), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(9, 0), 120, EstadoCita.PROGRAMADA);
        estados.cancelarPorPaciente(carmen.getIdCita(), null, "No puede asistir");
        Oportunidad op = abiertaDe(medico);
        assertThat(op.getInicio()).isEqualTo(manana.atTime(8, 0));

        mvc.perform(get("/recepcion/cupos/" + op.getIdOportunidad()).with(user(recepcion)))
            .andExpect(content().string(containsString("liberado 08:15 a 08:45")))
            .andExpect(content().string(containsString("Bloque libre 08:00 a 09:00")))
            .andExpect(content().string(containsString("se liberó el horario de 08:15 a 08:45")));
    }

    @Test
    void unCandidatoDeOtroMedicoDeLaEspecialidadPasaASuAgendaYElCupoQuedaCubierto() throws Exception {
        Medico otro = conHorario(datos.crearMedico("Colega", especialidad), LocalTime.of(14, 0), LocalTime.of(16, 0));
        Cita cancelada = datos.crearCita(medico, paciente(), manana.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(8, 0), 60, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(9, 30), 90, EstadoCita.PROGRAMADA);
        estados.cancelarPorClinica(cancelada.getIdCita(), null, "Médico en reunión");
        Oportunidad op = abiertaDe(medico);
        Cita delColega = datos.crearCita(otro, paciente(), manana.plusDays(2).atTime(14, 0), 30, EstadoCita.PROGRAMADA);
        preferencias.guardar(delColega.getIdCita(), new PreferenciaDatos(AlcancePreferencia.ESPECIALIDAD, 0, 5), null);
        assertThat(candidatos.candidatos(op.getIdOportunidad())).extracting(Candidato::idCita)
            .contains(delColega.getIdCita());

        mvc.perform(post("/recepcion/cupos/" + op.getIdOportunidad() + "/candidatos/" + delColega.getIdCita()
                + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "ACEPTO").param("posicion", "1").param("inicioPropuesto", manana + "T09:00"))
            .andExpect(flash().attribute("exito", containsString("Cupo cubierto")))
            .andExpect(flash().attribute("exito", containsString("quedó libre")));

        Cita movida = citas.findById(delColega.getIdCita()).orElseThrow();
        assertThat(movida.getMedico().getIdMedico()).isEqualTo(medico.getIdMedico());
        assertThat(movida.getInicio()).isEqualTo(manana.atTime(9, 0));
        Oportunidad cubierta = oportunidades.findById(op.getIdOportunidad()).orElseThrow();
        assertThat(cubierta.getEstado()).isEqualTo(EstadoOportunidad.CUBIERTA);
        assertThat(cubierta.getCitaCobertura().getIdCita()).isEqualTo(delColega.getIdCita());
        assertThat(abiertaDe(otro).getInicio()).isEqualTo(manana.plusDays(2).atTime(14, 0));
    }

    @Test
    void siElHorarioAnteriorEsContiguoAlCupoElCupoQuedaCubiertoYNoReemplazado() throws Exception {
        datos.crearCita(medico, paciente(), manana.atTime(8, 0), 90, EstadoCita.PROGRAMADA);
        Cita cancelada = datos.crearCita(medico, paciente(), manana.atTime(9, 30), 30, EstadoCita.PROGRAMADA);
        Cita candidato = datos.crearCita(medico, paciente(), manana.atTime(10, 0), 15, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(10, 15), 45, EstadoCita.PROGRAMADA);
        preferencias.guardar(candidato.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 1), null);
        estados.cancelarPorPaciente(cancelada.getIdCita(), null, "Viaje");
        Oportunidad op = abiertaDe(medico);
        assertThat(op.getInicio()).isEqualTo(manana.atTime(9, 30));

        mvc.perform(post("/recepcion/cupos/" + op.getIdOportunidad() + "/candidatos/" + candidato.getIdCita()
                + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "ACEPTO").param("posicion", "1").param("inicioPropuesto", manana + "T09:30"))
            .andExpect(flash().attribute("exito", containsString("Cupo cubierto")));

        Oportunidad despues = oportunidades.findById(op.getIdOportunidad()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoOportunidad.CUBIERTA);
        assertThat(despues.getCitaCobertura().getIdCita()).isEqualTo(candidato.getIdCita());
    }

    @Test
    void moverAlFinalSePuedeRepetirYNoSeOfreceAlUltimo() throws Exception {
        Cita cancelada = datos.crearCita(medico, paciente(), manana.atTime(8, 0), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(8, 30), 150, EstadoCita.PROGRAMADA);
        estados.cancelarPorPaciente(cancelada.getIdCita(), null, "Prueba");
        Oportunidad op = abiertaDe(medico);
        Cita a = datos.crearCita(medico, paciente(), manana.plusDays(3).atTime(8, 0), 30, EstadoCita.PROGRAMADA);
        Cita b = datos.crearCita(medico, paciente(), manana.plusDays(3).atTime(8, 30), 30, EstadoCita.PROGRAMADA);
        preferencias.guardar(a.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 5), null);
        preferencias.guardar(b.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 5), null);
        String base = "/recepcion/cupos/" + op.getIdOportunidad() + "/candidatos/";

        Long primero = orden(op).get(0);
        Long segundo = orden(op).get(1);
        mvc.perform(post(base + primero + "/postergar").with(user(recepcion)).with(csrf()))
            .andExpect(flash().attribute("exito", "Candidato movido al final de la lista."));
        assertThat(orden(op)).containsExactly(segundo, primero);
        mvc.perform(post(base + primero + "/postergar").with(user(recepcion)).with(csrf()))
            .andExpect(flash().attribute("aviso", "Ese candidato ya es el último de la lista."));
        mvc.perform(post(base + segundo + "/postergar").with(user(recepcion)).with(csrf()))
            .andExpect(flash().attribute("exito", "Candidato movido al final de la lista."));
        assertThat(orden(op)).containsExactly(primero, segundo);

        String html = mvc.perform(get("/recepcion/cupos/" + op.getIdOportunidad()).with(user(recepcion)))
            .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(base + primero + "/postergar").doesNotContain(base + segundo + "/postergar");
    }

    private List<Long> orden(Oportunidad op) {
        return candidatos.candidatos(op.getIdOportunidad()).stream().map(Candidato::idCita).toList();
    }

    @Test
    void laBusquedaDePacientesEsSoloPorDocumentoYNombre() throws Exception {
        Paciente p = paciente();
        mvc.perform(get("/recepcion/pacientes").param("q", p.getNroDocumento()).with(user(recepcion)))
            .andExpect(content().string(containsString(p.getNroDocumento())));
        mvc.perform(get("/recepcion/pacientes").param("q", "999000111").with(user(recepcion)))
            .andExpect(content().string(not(containsString(p.getNroDocumento()))));
    }

    @Test
    void entrarAUnaPantallaDeOtroPerfilDevuelve403() throws Exception {
        mvc.perform(get("/admin/usuarios").with(user(recepcion)))
            .andExpect(status().isForbidden());
    }
}
