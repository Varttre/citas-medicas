package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.NuevaCitaComando;
import pe.consultorio.citas.entity.CanalOrigen;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/** Horarios liberados: bloque completo, reemplazo por uno mayor, cobertura con sobrantes y expiración. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class OportunidadServiceTest {

    @Autowired CitaEstadoService estados;
    @Autowired ReservaService reserva;
    @Autowired OportunidadService oportunidadService;
    @Autowired DatosPrueba datos;
    @Autowired OportunidadRepository oportunidades;
    @Autowired TipoConsultaRepository tipos;

    Especialidad especialidad;
    Medico medico;
    LocalDate dia = LocalDate.now().plusDays(1);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Geriatría");
        medico = datos.crearMedico("Libera", especialidad);
        datos.crearHorario(medico, LocalDate.now().minusDays(1), dia.getDayOfWeek().getValue(), LocalTime.of(8, 0),
            LocalTime.of(14, 0));
    }

    private Cita cita(int h, int m, int minutos) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            dia.atTime(h, m), minutos, EstadoCita.PROGRAMADA);
    }

    private List<Oportunidad> delMedico() {
        return oportunidades.findAll().stream()
            .filter(o -> o.getMedico().getIdMedico().equals(medico.getIdMedico())).toList();
    }

    private Oportunidad abierta() {
        List<Oportunidad> a = delMedico().stream().filter(o -> o.getEstado() == EstadoOportunidad.ABIERTA).toList();
        assertThat(a).hasSize(1);
        return a.get(0);
    }

    @Test
    void elBloqueLiberadoCreceSeReemplazaYAlCubrirseVuelveAOfrecerLoQueSobra() {
        cita(8, 0, 210);
        Cita mediodia = cita(12, 0, 30);
        Cita tarde = cita(12, 30, 90);

        estados.cancelarPorClinica(mediodia.getIdCita(), null, "El médico tiene una reunión");
        Oportunidad primera = abierta();
        assertThat(primera.getInicio()).isEqualTo(dia.atTime(11, 30));
        assertThat(primera.getFin()).isEqualTo(dia.atTime(12, 30));

        estados.cancelarPorClinica(tarde.getIdCita(), null, "El médico tiene una reunión");
        Oportunidad mayor = abierta();
        assertThat(mayor.getInicio()).isEqualTo(dia.atTime(11, 30));
        assertThat(mayor.getFin()).isEqualTo(dia.atTime(14, 0));
        assertThat(mayor.getOportunidadPadre().getIdOportunidad()).isEqualTo(primera.getIdOportunidad());
        assertThat(oportunidades.findById(primera.getIdOportunidad()).orElseThrow().getEstado())
            .isEqualTo(EstadoOportunidad.REEMPLAZADA);

        Short idTipo = tipos.findAll().get(0).getIdTipoConsulta();
        Cita nueva = reserva.agendar(new NuevaCitaComando(datos.crearPaciente(String.valueOf(System.nanoTime()
            % 100000000)).getIdPaciente(), null, medico.getIdMedico(), especialidad.getIdEspecialidad(), idTipo,
            dia.atTime(11, 30), 30, Modalidad.PRESENCIAL, CanalOrigen.PRESENCIAL, null, null, null, null, false));
        Oportunidad cubierta = oportunidades.findById(mayor.getIdOportunidad()).orElseThrow();
        assertThat(cubierta.getEstado()).isEqualTo(EstadoOportunidad.CUBIERTA);
        assertThat(cubierta.getCitaCobertura().getIdCita()).isEqualTo(nueva.getIdCita());
        Oportunidad sobrante = abierta();
        assertThat(sobrante.getInicio()).isEqualTo(dia.atTime(12, 0));
        assertThat(sobrante.getFin()).isEqualTo(dia.atTime(14, 0));
        assertThat(sobrante.getOportunidadPadre().getIdOportunidad()).isEqualTo(mayor.getIdOportunidad());
    }

    @Test
    void acortarUnaCitaOfreceElSobranteSiEsUtil() {
        Cita larga = cita(9, 0, 60);
        cita(10, 0, 240);
        cita(8, 0, 60);
        estados.ajustarDuracion(larga.getIdCita(), 30, null);
        Oportunidad o = abierta();
        assertThat(o.getInicio()).isEqualTo(dia.atTime(9, 30));
        assertThat(o.getFin()).isEqualTo(dia.atTime(10, 0));
    }

    @Test
    void unaCitaPasadaNoGeneraOportunidadYLasVencidasExpiran() {
        Medico m = datos.crearMedico("Pasado", especialidad);
        Cita ayer = datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)),
            LocalDate.now().minusDays(1).atTime(9, 0), 30, EstadoCita.PROGRAMADA);
        estados.cancelarPorClinica(ayer.getIdCita(), null, "Prueba");
        assertThat(oportunidades.findAll()).noneMatch(o -> o.getMedico().getIdMedico().equals(m.getIdMedico()));

        Oportunidad vieja = new Oportunidad();
        vieja.setCitaOrigen(ayer);
        vieja.setMedico(m);
        vieja.setInicio(LocalDate.now().minusDays(1).atTime(9, 0));
        vieja.setFin(LocalDate.now().minusDays(1).atTime(9, 30));
        vieja.setEstado(EstadoOportunidad.ABIERTA);
        vieja.setCreadaEn(LocalDateTime.now().minusDays(1));
        Long id = oportunidades.save(vieja).getIdOportunidad();
        assertThat(oportunidadService.expirarVencidas()).isGreaterThanOrEqualTo(1);
        Oportunidad r = oportunidades.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoOportunidad.EXPIRADA);
        assertThat(r.getCerradaEn()).isNotNull();
    }
}
