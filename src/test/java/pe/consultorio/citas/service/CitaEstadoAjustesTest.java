package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.CitaEvento;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.ModalidadPermitida;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;
import pe.consultorio.citas.entity.TipoConsulta;
import pe.consultorio.citas.entity.TipoEvento;
import pe.consultorio.citas.exception.HorarioOcupadoException;
import pe.consultorio.citas.exception.PlazoVencidoException;
import pe.consultorio.citas.exception.RecursoNoEncontradoException;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CitaEventoRepository;
import pe.consultorio.citas.repository.CitaRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;
import pe.consultorio.citas.repository.TipoConsultaRepository;

/** Confirmación desde la cola y ajuste de duración: casos aceptados y rechazos que no deben tocar los datos. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class CitaEstadoAjustesTest {

    @Autowired CitaEstadoService estados;
    @Autowired DatosPrueba datos;
    @Autowired CitaRepository citas;
    @Autowired CitaEventoRepository eventos;
    @Autowired RegistroLlamadaRepository llamadas;
    @Autowired TipoConsultaRepository tipos;

    Medico medico;
    Cuenta recepcion;
    static final AtomicInteger DOCUMENTO = new AtomicInteger(84000000);

    @BeforeEach
    void preparar() {
        medico = datos.crearMedico("Ajustes", datos.crearEspecialidad("Neumología"));
        recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION);
    }

    private static LocalDateTime enRejilla(int minutos) {
        LocalDateTime t = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            .plusMinutes((LocalDateTime.now().getMinute() / 15) * 15L);
        return t.plusMinutes(minutos);
    }

    private Cita cita(LocalDateTime inicio, int minutos) {
        return datos.crearCita(medico, datos.crearPaciente(String.valueOf(DOCUMENTO.getAndIncrement())), inicio,
            minutos, EstadoCita.PROGRAMADA);
    }

    private RegistroLlamada llamada(Cita c, ResultadoLlamada resultado) {
        RegistroLlamada r = new RegistroLlamada();
        r.setCuentaOperador(recepcion);
        r.setTipoCola(TipoCola.CONFIRMACION);
        r.setCita(c);
        r.setTelefonoLlamado("999000111");
        r.setResultado(resultado);
        r.setFechaHora(LocalDateTime.now());
        return llamadas.save(r);
    }

    private List<CitaEvento> eventosDe(Cita c) {
        return eventos.findAll().stream().filter(e -> e.getCita().getIdCita().equals(c.getIdCita())).toList();
    }

    private Cita releer(Cita c) {
        return citas.findById(c.getIdCita()).orElseThrow();
    }

    @Test
    void confirmarConLaLlamadaDejaElEventoEnlazado() {
        Cita c = cita(enRejilla(24 * 60), 30);
        RegistroLlamada l = llamada(c, ResultadoLlamada.CONFIRMO);
        estados.confirmar(c.getIdCita(), l.getIdLlamada(), recepcion.getIdCuenta());

        assertThat(releer(c).getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        List<CitaEvento> ev = eventosDe(c);
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).getEstadoNuevo()).isEqualTo(EstadoCita.CONFIRMADA);
        assertThat(ev.get(0).getLlamada().getIdLlamada()).isEqualTo(l.getIdLlamada());
    }

    @Test
    void confirmarSinLlamadaValidaSeRechazaYNoCambiaNada() {
        Cita c = cita(enRejilla(24 * 60), 30);
        RegistroLlamada noContesto = llamada(c, ResultadoLlamada.NO_CONTESTO);

        assertThatThrownBy(() -> estados.confirmar(c.getIdCita(), null, recepcion.getIdCuenta()))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> estados.confirmar(c.getIdCita(), noContesto.getIdLlamada(), recepcion.getIdCuenta()))
            .hasMessageContaining("llamada");
        assertThat(releer(c).getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(eventosDe(c)).isEmpty();
    }

    @Test
    void confirmarAntesDeEntrarALaColaSeRechaza() {
        Cita lejana = cita(enRejilla(5 * 24 * 60), 30);
        RegistroLlamada l = llamada(lejana, ResultadoLlamada.CONFIRMO);
        assertThatThrownBy(() -> estados.confirmar(lejana.getIdCita(), l.getIdLlamada(), recepcion.getIdCuenta()))
            .isInstanceOf(PlazoVencidoException.class)
            .hasMessageContaining("cola de confirmaciones");
        assertThat(releer(lejana).getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void alargarUnaCitaMueveSuFinYDejaEvento() {
        Cita c = cita(enRejilla(24 * 60), 30);
        estados.ajustarDuracion(c.getIdCita(), 45, recepcion.getIdCuenta());

        Cita r = releer(c);
        assertThat(r.getInicio()).isEqualTo(c.getInicio());
        assertThat(r.getFin()).isEqualTo(c.getInicio().plusMinutes(45));
        CitaEvento e = eventosDe(c).get(0);
        assertThat(e.getTipoEvento()).isEqualTo(TipoEvento.AJUSTE_DURACION);
        assertThat(e.getFinAnterior()).isEqualTo(c.getFin());
        assertThat(e.getFinNuevo()).isEqualTo(r.getFin());
    }

    @Test
    void unaDuracionQueSeCruzaConLaSiguienteSeRechazaYLaCitaQuedaIgual() {
        LocalDateTime t = enRejilla(24 * 60);
        Cita primera = cita(t, 30);
        cita(t.plusMinutes(30), 30);

        assertThatThrownBy(() -> estados.ajustarDuracion(primera.getIdCita(), 45, recepcion.getIdCuenta()))
            .isInstanceOf(HorarioOcupadoException.class)
            .hasMessage("La nueva duración se cruza con la siguiente cita.");
        assertThat(releer(primera).getFin()).isEqualTo(t.plusMinutes(30));
        assertThat(eventosDe(primera)).isEmpty();
    }

    @Test
    void duracionFueraDeLaRejillaOSinPermisoSeRechaza() {
        Cita c = cita(enRejilla(24 * 60), 30);
        assertThatThrownBy(() -> estados.ajustarDuracion(c.getIdCita(), 20, recepcion.getIdCuenta()))
            .hasMessageContaining("múltiplos");

        TipoConsulta sinAjuste = new TipoConsulta();
        sinAjuste.setNombre("Sin ajuste " + DOCUMENTO.getAndIncrement());
        sinAjuste.setDuracionDefecto((short) 30);
        sinAjuste.setAjusteMedico(false);
        sinAjuste.setAjusteRecepcion(false);
        sinAjuste.setReservableWeb(false);
        sinAjuste.setModalidadPermitida(ModalidadPermitida.AMBAS);
        sinAjuste.setActivo(true);
        TipoConsulta guardado = tipos.save(sinAjuste);
        Cita conTipo = releer(c);
        conTipo.setTipoConsulta(guardado);
        citas.save(conTipo);

        Long cuentaMedico = medico.getCuenta().getIdCuenta();
        assertThatThrownBy(() -> estados.ajustarDuracion(c.getIdCita(), 45, cuentaMedico))
            .hasMessageContaining("no puede ajustar");
        Long otroMedico = datos.crearMedico("Otro", datos.crearEspecialidad("Neumología")).getCuenta().getIdCuenta();
        assertThatThrownBy(() -> estados.ajustarDuracion(c.getIdCita(), 45, otroMedico))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(releer(c).getFin()).isEqualTo(c.getFin());
    }
}
