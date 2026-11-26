package pe.consultorio.citas.controller;

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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.CuentaPaciente;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.CuentaPacienteRepository;
import pe.consultorio.citas.repository.CuentaRepository;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.service.CandidatoService;
import pe.consultorio.citas.service.CitaEstadoService;
import pe.consultorio.citas.service.PreferenciaService;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/**
Correcciones pedidas por el usuario al revisar lo subido (2026-11-25): «Para otra persona» sin el titular y sin
tercero elegido por defecto, cupos cubiertos a la vista y cubrir eligiendo la hora, agenda por paciente y día, y
«Mi horario» que deja claro que una ausencia quita horas.
*/
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CorreccionesPostSubidaTest {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioDetallesService detalles;
    @Autowired RegistroService registro;
    @Autowired CuentaRepository cuentas;
    @Autowired CuentaPacienteRepository vinculos;
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
        especialidad = datos.crearEspecialidad("Neumología");
        medico = datos.crearMedico("Correcciones", especialidad);
        for (int d = 1; d <= 7; d++) {
            datos.crearHorario(medico, LocalDate.now().minusDays(1), d, LocalTime.of(8, 0), LocalTime.of(11, 0));
        }
        String correo = "recepcorr" + System.nanoTime() + "@prueba.pe";
        usuarios.crearPersonal(correo, "Clave2026!", NombreRol.RECEPCION, "Recepción", null);
        recepcion = detalles.loadUserByUsername(correo);
    }

    private Paciente paciente() {
        return datos.crearPaciente(String.valueOf(10000000 + System.nanoTime() % 89999999));
    }

    /** Cuenta de paciente con el vínculo de su paciente propio, como la deja el registro real. */
    private Cuenta cuentaConVinculoPropio() {
        Cuenta c = datos.crearCuentaPaciente("corr" + System.nanoTime() + "@prueba.pe");
        CuentaPaciente v = new CuentaPaciente();
        v.setCuenta(c);
        v.setPaciente(c.getPacientePropio());
        v.setConsentimientoEn(LocalDateTime.now());
        vinculos.save(v);
        return c;
    }

    /** PA-167 */
    @Test
    void paraOtraPersonaNoOfreceAlTitularNiEligeUnTerceroPorDefecto() throws Exception {
        Cuenta cuenta = cuentaConVinculoPropio();
        UsuarioSesion u = detalles.loadUserByUsername(cuenta.getCorreo());
        Paciente propio = cuenta.getPacientePropio();
        String docHijo = String.valueOf(70000000 + System.nanoTime() % 9999999);
        Long idHijo = registro.vincularTercero(cuenta.getIdCuenta(), new TerceroForm(TipoDocumento.DNI, docHijo, "Hijo",
            "Prueba", LocalDate.of(2015, 3, 3), null, true)).idPaciente();

        assertThat(registro.terceros(cuenta.getIdCuenta())).extracting(t -> t.idPaciente()).containsExactly(idHijo);
        mvc.perform(get("/paciente/cita/nueva").with(user(u)))
            .andExpect(content().string(containsString(docHijo)))
            .andExpect(content().string(not(containsString(propio.getNroDocumento()))))
            .andExpect(content().string(not(containsString("value=\"" + idHijo + "\" checked"))));

        mvc.perform(post("/paciente/cita/nueva").with(user(u)).with(csrf()).param("paraQuien", "OTRA"))
            .andExpect(redirectedUrl("/paciente/cita/nueva"))
            .andExpect(flash().attribute("error", containsString("Elige para cuál de tus terceros")));
        mvc.perform(post("/paciente/cita/nueva").with(user(u)).with(csrf()).param("paraQuien", "OTRA")
                .param("idTercero", propio.getIdPaciente().toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva"))
            .andExpect(flash().attribute("error", RegistroService.ES_TU_DOCUMENTO));
        mvc.perform(post("/paciente/cita/nueva").with(user(u)).with(csrf()).param("paraQuien", "OTRA")
                .param("idTercero", "NUEVO").param("tipoDocumento", "DNI").param("nroDocumento", propio.getNroDocumento())
                .param("nombres", propio.getNombres()).param("apellidos", propio.getApellidos())
                .param("fechaNacimiento", propio.getFechaNacimiento().toString()).param("consentimiento", "true"))
            .andExpect(redirectedUrl("/paciente/cita/nueva"))
            .andExpect(flash().attribute("error", RegistroService.ES_TU_DOCUMENTO));
        mvc.perform(post("/paciente/cita/nueva").with(user(u)).with(csrf()).param("paraQuien", "OTRA")
                .param("idTercero", idHijo.toString()))
            .andExpect(redirectedUrl("/paciente/cita/nueva/2"));
    }

    /** PA-168 */
    @Test
    void recepcionCubreUnCupoEligiendoLaHoraYElCupoCubiertoQuedaAparte() throws Exception {
        datos.crearCita(medico, paciente(), manana.atTime(8, 0), 60, EstadoCita.PROGRAMADA);
        Cita cancelada = datos.crearCita(medico, paciente(), manana.atTime(9, 0), 60, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, paciente(), manana.atTime(10, 0), 60, EstadoCita.PROGRAMADA);
        estados.cancelarPorPaciente(cancelada.getIdCita(), null, "No puede asistir");
        Oportunidad op = oportunidades.findAll().stream()
            .filter(o -> o.getMedico().getIdMedico().equals(medico.getIdMedico())
                && o.getEstado() == EstadoOportunidad.ABIERTA)
            .findFirst().orElseThrow();
        Paciente rosa = paciente();
        Cita suya = datos.crearCita(medico, rosa, manana.plusDays(2).atTime(8, 0), 30, EstadoCita.PROGRAMADA);
        preferencias.guardar(suya.getIdCita(), new PreferenciaDatos(AlcancePreferencia.MEDICO, 0, 5), null);

        Candidato ca = candidatos.candidatos(op.getIdOportunidad()).stream()
            .filter(x -> x.idCita().equals(suya.getIdCita())).findFirst().orElseThrow();
        List<LocalDateTime> horas = candidatos.horasPosibles(op.getIdOportunidad(), ca);
        assertThat(horas).containsExactly(manana.atTime(9, 0), manana.atTime(9, 15), manana.atTime(9, 30));
        mvc.perform(get("/recepcion/cupos/" + op.getIdOportunidad()).with(user(recepcion)))
            .andExpect(content().string(containsString("¿a qué hora pasa?")))
            .andExpect(content().string(containsString(manana + "T09:30")));

        mvc.perform(post("/recepcion/cupos/" + op.getIdOportunidad() + "/candidatos/" + suya.getIdCita()
                + "/resultado").with(user(recepcion)).with(csrf())
                .param("resultado", "ACEPTO").param("posicion", "1").param("inicioPropuesto", manana + "T09:30"))
            .andExpect(flash().attribute("exito", containsString("Cupo cubierto")));
        Cita movida = citas.findById(suya.getIdCita()).orElseThrow();
        assertThat(movida.getInicio()).isEqualTo(manana.atTime(9, 30));
        assertThat(oportunidades.findById(op.getIdOportunidad()).orElseThrow().getEstado())
            .isEqualTo(EstadoOportunidad.CUBIERTA);

        String nombre = rosa.getNombres() + " " + rosa.getApellidos();
        mvc.perform(get("/recepcion/cupos").with(user(recepcion)))
            .andExpect(content().string(containsString("Cubiertos hoy")))
            .andExpect(content().string(containsString(nombre)))
            .andExpect(content().string(containsString(nombre + " cubrió 09:30 a 10:00")));
    }

    /** PA-169 */
    @Test
    void laAgendaCombinaLaBusquedaPorPacienteConElDiaElegido() throws Exception {
        Paciente p = paciente();
        datos.crearCita(medico, p, manana.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        datos.crearCita(medico, p, manana.plusDays(3).atTime(9, 0), 30, EstadoCita.PROGRAMADA);

        mvc.perform(get("/recepcion/agenda").with(user(recepcion)).param("q", p.getNroDocumento())
                .param("fecha", manana.toString()))
            .andExpect(content().string(containsString(manana.format(FECHA))))
            .andExpect(content().string(not(containsString(manana.plusDays(3).format(FECHA)))))
            .andExpect(content().string(containsString("1 cita")));
        mvc.perform(get("/recepcion/agenda").with(user(recepcion)).param("q", p.getNroDocumento()).param("fecha", ""))
            .andExpect(content().string(containsString(manana.format(FECHA))))
            .andExpect(content().string(containsString(manana.plusDays(3).format(FECHA))))
            .andExpect(content().string(containsString("todas las fechas")));
    }

    /** PA-170 */
    @Test
    void miHorarioExplicaQueUnaAusenciaQuitaHorasYNoAmplia() throws Exception {
        UsuarioSesion u = detalles.loadUserByUsername(cuentas.findById(medico.getCuenta().getIdCuenta())
            .orElseThrow().getCorreo());
        mvc.perform(get("/medico/horario").with(user(u)))
            .andExpect(content().string(containsString("Ausencias (excepciones)")))
            .andExpect(content().string(containsString("No sirve para ampliar el horario")))
            .andExpect(content().string(containsString("Para atender más horas, cambia aquí tu horario.")));
    }
}
