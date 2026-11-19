package pe.consultorio.citas.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
import pe.consultorio.citas.dto.ParametrosForm;
import pe.consultorio.citas.dto.PlazosForm;
import pe.consultorio.citas.dto.TipoConsultaForm;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.ParametroConsultorio;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.Transicion;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.AuditoriaRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;
import pe.consultorio.citas.service.AccionAuditoria;
import pe.consultorio.citas.service.ConfiguracionService;
import pe.consultorio.citas.service.ConfiguracionService.ConfirmacionRequeridaException;
import pe.consultorio.citas.service.UsuarioDetallesService;
import pe.consultorio.citas.service.UsuarioService;

/** Configuración del administrador: guardar, validar como la base, auditar y confirmar la desactivación. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class ConfiguracionAdminTest {

    @Autowired MockMvc mvc;
    @Autowired ConfiguracionService configuracion;
    @Autowired UsuarioService usuarioService;
    @Autowired UsuarioDetallesService detalles;
    @Autowired AuditoriaRepository auditorias;
    @Autowired TipoConsultaRepository tipos;
    @Autowired CitaRepository citas;
    @Autowired DatosPrueba datos;

    UsuarioSesion admin;

    @BeforeEach
    void preparar() {
        String correo = "config" + System.nanoTime() + "@prueba.pe";
        usuarioService.crearPersonal(correo, "Clave2026!", NombreRol.ADMINISTRADOR, "Admin de configuración", null);
        admin = detalles.loadUserByUsername(correo);
    }

    private ParametrosForm actuales(short bloqueo) {
        ParametroConsultorio p = configuracion.parametros();
        return new ParametrosForm(p.getMinutosProtegidos(), p.getVentanaLiberacionH(), bloqueo,
            p.getNVentanaEstimacion(), p.getDiasRecordarSugerencia(), p.getAnticipacionConfirmH(),
            p.getVigenciaIndicadaDias(), p.isMostrarPenalizacion(), p.getTextoPenalizacion(),
            p.getTipoWebDefecto().getIdTipoConsulta(), p.getWhatsappNumero(), p.getMaxIntentosLogin(),
            p.getMinutosBloqueoLogin());
    }

    private TipoConsultaForm tipo(String nombre, int duracion, Short incremento, boolean activo) {
        return new TipoConsultaForm(nombre, (short) duracion, incremento, true, true, false, ModalidadPermitida.AMBAS,
            activo);
    }

    @Test
    void lasCuatroPestanasAbren() throws Exception {
        for (String p : new String[] {"tipos", "parametros", "plazos", "catalogos"}) {
            mvc.perform(get("/admin/configuracion").param("pestana", p).with(user(admin))).andExpect(status().isOk());
        }
    }

    @Test
    void unCambioDeParametroQuedaEnLaAuditoria() {
        short original = configuracion.parametros().getBloqueoTemporalMin();
        short nuevo = (short) (original == 7 ? 8 : 7);
        try {
            configuracion.guardarParametros(actuales(nuevo), admin.getIdCuenta());
            assertThat(configuracion.parametros().getBloqueoTemporalMin()).isEqualTo(nuevo);
            assertThat(auditorias.findAll()).anyMatch(a -> AccionAuditoria.CONFIG_CAMBIO.equals(a.getAccion())
                && a.getDetalle().contains("bloqueo_temporal_min: " + original + " → " + nuevo));
        } finally {
            configuracion.guardarParametros(actuales(original), admin.getIdCuenta());
        }
    }

    @Test
    void penalizacionSinTextoSeRechazaYNoGuardaNada() {
        ParametrosForm a = actuales(configuracion.parametros().getBloqueoTemporalMin());
        ParametrosForm sinTexto = new ParametrosForm(a.minutosProtegidos(), a.ventanaLiberacionH(),
            (short) 9, a.nVentanaEstimacion(), a.diasRecordarSugerencia(), a.anticipacionConfirmH(),
            a.vigenciaIndicadaDias(), true, " ", a.idTipoWebDefecto(), a.whatsappNumero(), a.maxIntentosLogin(),
            a.minutosBloqueoLogin());
        assertThatThrownBy(() -> configuracion.guardarParametros(sinTexto, admin.getIdCuenta()))
            .hasMessageContaining("penalización");
        assertThat(configuracion.parametros().getBloqueoTemporalMin()).isEqualTo(a.bloqueoTemporalMin());
    }

    @Test
    void unIncrementoDe20NoSePuedeGuardar() throws Exception {
        String nombre = "Tipo veinte " + System.nanoTime();
        assertThatThrownBy(() -> configuracion.guardarTipo(null, tipo(nombre, 30, (short) 20, true), false,
            admin.getIdCuenta())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> configuracion.guardarTipo(null, tipo(nombre, 20, null, true), false,
            admin.getIdCuenta())).hasMessageContaining("múltiplo de 15");
        mvc.perform(post("/admin/configuracion/tipos").with(user(admin)).with(csrf())
                .param("nombre", nombre).param("duracionDefecto", "30").param("incrementoMin", "20")
                .param("modalidadPermitida", "AMBAS").param("activo", "true"))
            .andExpect(flash().attributeExists("error"));
        assertThat(tipos.findAll()).noneMatch(t -> t.getNombre().equals(nombre));
    }

    @Test
    void desactivarUnTipoConCitasFuturasPideConfirmacion() {
        TipoConsulta t = configuracion.guardarTipo(null, tipo("Control " + System.nanoTime(), 30, null, true), false,
            admin.getIdCuenta());
        Medico m = datos.crearMedico("Config", datos.crearEspecialidad("Reumatología"));
        LocalDateTime manana = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusDays(1);
        Cita c = datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), manana, 30,
            EstadoCita.PROGRAMADA);
        c.setTipoConsulta(t);
        citas.save(c);

        Short id = t.getIdTipoConsulta();
        assertThatThrownBy(() -> configuracion.guardarTipo(id, tipo(t.getNombre(), 30, null, false), false,
            admin.getIdCuenta())).isInstanceOf(ConfirmacionRequeridaException.class)
            .hasMessage("Hay 1 citas futuras de este tipo.");
        assertThat(tipos.findById(id).orElseThrow().isActivo()).isTrue();

        configuracion.desactivarTipo(id, admin.getIdCuenta());
        assertThat(tipos.findById(id).orElseThrow().isActivo()).isFalse();
        assertThat(citas.findById(c.getIdCita()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void losPlazosSeGuardanComoMinutosRelativosAlInicio() {
        short cancelarAntes = (short) -configuracion.plazo(Transicion.CANCELADA_POR_PACIENTE).getMinutosHasta();
        short llegadaAntes = (short) -configuracion.plazo(Transicion.ASISTIO).getMinutosDesde();
        short noAsistio = configuracion.plazo(Transicion.NO_ASISTIO).getMinutosDesde();
        try {
            configuracion.guardarPlazos(new PlazosForm(noAsistio, (short) 60, llegadaAntes), admin.getIdCuenta());
            assertThat(configuracion.plazo(Transicion.CANCELADA_POR_PACIENTE).getMinutosHasta()).isEqualTo((short) -60);
        } finally {
            configuracion.guardarPlazos(new PlazosForm(noAsistio, cancelarAntes, llegadaAntes), admin.getIdCuenta());
        }
    }
}
