package pe.consultorio.citas.controller.paciente;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.TerceroForm;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.entity.TipoDocumento;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.PreferenciaDisponibilidadRepository;
import pe.consultorio.citas.service.RegistroService;
import pe.consultorio.citas.service.UsuarioDetallesService;

/** «Mis citas» muestra lo que corresponde a cada cuenta y deja activar o retirar la disponibilidad inmediata. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class MisCitasTercerosTest {

    @Autowired MockMvc mvc;
    @Autowired DatosPrueba datos;
    @Autowired UsuarioDetallesService detalles;
    @Autowired RegistroService registro;
    @Autowired CitaRepository citas;
    @Autowired PreferenciaDisponibilidadRepository preferencias;

    Medico medico;
    LocalDate dia = LocalDate.now().plusDays(4);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Terceros", datos.crearEspecialidad("Oncología"));
    }

    private Cuenta cuentaPaciente() {
        return datos.crearCuentaPaciente("mis" + System.nanoTime() + "@prueba.pe");
    }

    @Test
    void laCuentaVeLaCitaQueAgendoParaUnTerceroPeroNoOtraDelTercero() throws Exception {
        Cuenta madre = cuentaPaciente();
        String doc = String.valueOf(74000000 + System.nanoTime() % 999999);
        Long idHijo = registro.vincularTercero(madre.getIdCuenta(), new TerceroForm(TipoDocumento.DNI, doc, "Hijo",
            "Tercero", LocalDate.of(2016, 1, 1), null, true)).idPaciente();
        Paciente hijo = new Paciente();
        hijo.setIdPaciente(idHijo);

        Cita agendadaPorMadre = datos.crearCita(medico, hijo, dia.atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        agendadaPorMadre.setCuentaTitular(madre);
        citas.save(agendadaPorMadre);
        datos.crearCita(medico, hijo, dia.atTime(11, 0), 30, EstadoCita.PROGRAMADA);   // la agendó recepción
        Cita propia = datos.crearCita(medico, madre.getPacientePropio(), dia.atTime(15, 0), 30, EstadoCita.PROGRAMADA);

        mvc.perform(get("/paciente/citas").with(user(detalles.loadUserByUsername(madre.getCorreo()))))
            .andExpect(content().string(containsString("Para Hijo Tercero")))
            .andExpect(content().string(containsString("/paciente/citas/" + agendadaPorMadre.getIdCita() + "/reprogramar")))
            .andExpect(content().string(containsString("/paciente/citas/" + propia.getIdCita() + "/reprogramar")))
            .andExpect(content().string(containsString("2 próximas citas")));
    }

    @Test
    void activaYRetiraLaDisponibilidadInmediataDeSuCita() throws Exception {
        Cuenta cuenta = cuentaPaciente();
        Cita c = datos.crearCita(medico, cuenta.getPacientePropio(), dia.atTime(10, 0), 30, EstadoCita.PROGRAMADA);
        var sesion = user(detalles.loadUserByUsername(cuenta.getCorreo()));

        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/preferencia").with(sesion).with(csrf())
                .param("alcance", "ESPECIALIDAD").param("llegada", "45").param("diasPrevios", "2"))
            .andExpect(flash().attribute("exito", containsString("te llamaremos")));
        assertThat(preferencias.findByCitaIdCita(c.getIdCita()).orElseThrow().getMinLlegadaMin()).isEqualTo((short) 45);
        mvc.perform(get("/paciente/citas").with(sesion))
            .andExpect(content().string(containsString("Te llamaremos si se libera un cupo")))
            .andExpect(content().string(containsString("Puedes cancelar hasta las")));

        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/preferencia/revocar").with(sesion).with(csrf()));
        assertThat(preferencias.findByCitaIdCita(c.getIdCita()).orElseThrow().getRevocadoEn()).isNotNull();
        mvc.perform(get("/paciente/citas").with(sesion))
            .andExpect(content().string(not(containsString("Te llamaremos si se libera un cupo"))));
    }

    @Test
    void otraCuentaNoTocaLaPreferenciaDeUnaCitaAjena() throws Exception {
        Cuenta duena = cuentaPaciente();
        Cita c = datos.crearCita(medico, duena.getPacientePropio(), dia.atTime(16, 0), 30, EstadoCita.PROGRAMADA);
        Cuenta ajena = cuentaPaciente();
        mvc.perform(post("/paciente/citas/" + c.getIdCita() + "/preferencia")
                .with(user(detalles.loadUserByUsername(ajena.getCorreo()))).with(csrf())
                .param("alcance", "MEDICO").param("llegada", "30").param("diasPrevios", "1"))
            .andExpect(status().isNotFound());
        assertThat(preferencias.findByCitaIdCita(c.getIdCita())).isEmpty();
    }
}
