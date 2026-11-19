package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.Candidato;
import pe.consultorio.citas.dto.PreferenciaDatos;
import pe.consultorio.citas.entity.AlcancePreferencia;
import pe.consultorio.citas.entity.Cita;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.EstadoCita;
import pe.consultorio.citas.entity.EstadoOportunidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.entity.Modalidad;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.entity.Oportunidad;
import pe.consultorio.citas.entity.RegistroLlamada;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.entity.TipoCola;
import pe.consultorio.citas.repository.OportunidadRepository;
import pe.consultorio.citas.repository.RegistroLlamadaRepository;

/** Quién entra en la lista de un horario liberado, con qué puntaje y en qué orden. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class CandidatoServiceTest {

    @Autowired CandidatoService candidatos;
    @Autowired PreferenciaService preferencias;
    @Autowired DatosPrueba datos;
    @Autowired OportunidadRepository oportunidades;
    @Autowired RegistroLlamadaRepository llamadas;

    Especialidad especialidad;
    Medico medico;
    Medico colega;
    Oportunidad op;
    Cuenta recepcion;
    LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void preparar() {
        especialidad = datos.crearEspecialidad("Infectología");
        medico = datos.crearMedico("Bloque", especialidad);
        colega = datos.crearMedico("Colega", especialidad);
        recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION);
        Cita origen = cita(medico, manana.atTime(10, 0), 30, EstadoCita.CANCELADA_POR_PACIENTE);
        Oportunidad o = new Oportunidad();
        o.setCitaOrigen(origen);
        o.setMedico(medico);
        o.setInicio(manana.atTime(10, 0));
        o.setFin(manana.atTime(10, 30));
        o.setEstado(EstadoOportunidad.ABIERTA);
        o.setCreadaEn(LocalDateTime.now());
        op = oportunidades.save(o);
    }

    private Cita cita(Medico m, LocalDateTime inicio, int minutos, EstadoCita estado) {
        return datos.crearCita(m, datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)), inicio, minutos,
            estado);
    }

    private Cita conPreferencia(Medico m, LocalDateTime inicio, int minutos, AlcancePreferencia alcance,
            int diasPrevios) {
        Cita c = cita(m, inicio, minutos, EstadoCita.PROGRAMADA);
        preferencias.guardar(c.getIdCita(), new PreferenciaDatos(alcance, 0, diasPrevios), null);
        return c;
    }

    private List<Long> idsEnLista(Set<Long> mias) {
        return candidatos.candidatos(op.getIdOportunidad()).stream().map(Candidato::idCita)
            .filter(mias::contains).toList();
    }

    @Test
    void soloEntranLosQueCumplenTodasLasCondiciones() {
        LocalDateTime en3 = manana.plusDays(2).atTime(9, 0);
        Cita cumple = conPreferencia(medico, en3, 30, AlcancePreferencia.MEDICO, 5);
        Cita porEspecialidad = conPreferencia(colega, en3.plusHours(1), 30, AlcancePreferencia.ESPECIALIDAD, 5);

        Cita revocada = conPreferencia(medico, en3.plusHours(2), 30, AlcancePreferencia.MEDICO, 5);
        preferencias.revocar(revocada.getIdCita(), null);
        Cita antesDelBloque = conPreferencia(medico, manana.atTime(8, 0), 30, AlcancePreferencia.MEDICO, 5);
        Cita otroMedico = conPreferencia(colega, en3.plusHours(3), 30, AlcancePreferencia.MEDICO, 5);
        Cita pocosDias = conPreferencia(medico, manana.plusDays(5).atTime(9, 0), 30, AlcancePreferencia.MEDICO, 1);
        Cita noCabe = conPreferencia(medico, en3.plusHours(4), 60, AlcancePreferencia.MEDICO, 5);
        Cita ocupado = conPreferencia(medico, en3.plusHours(5), 30, AlcancePreferencia.MEDICO, 5);
        datos.crearCita(colega, ocupado.getPaciente(), manana.atTime(10, 0), 30, EstadoCita.PROGRAMADA);
        Cita yaAcepto = conPreferencia(medico, en3.plusHours(6), 30, AlcancePreferencia.MEDICO, 5);
        RegistroLlamada r = new RegistroLlamada();
        r.setCuentaOperador(recepcion);
        r.setTipoCola(TipoCola.CUPO_LIBERADO);
        r.setOportunidad(op);
        r.setCita(yaAcepto);
        r.setPosicionLista((short) 1);
        r.setTelefonoLlamado("999000111");
        r.setResultado(ResultadoLlamada.ACEPTO);
        r.setFechaHora(LocalDateTime.now());
        llamadas.save(r);

        Set<Long> mias = Set.of(cumple.getIdCita(), porEspecialidad.getIdCita(), revocada.getIdCita(),
            antesDelBloque.getIdCita(), otroMedico.getIdCita(), pocosDias.getIdCita(), noCabe.getIdCita(),
            ocupado.getIdCita(), yaAcepto.getIdCita());
        assertThat(idsEnLista(mias)).containsExactly(cumple.getIdCita(), porEspecialidad.getIdCita());

        Candidato primero = candidatos.candidatos(op.getIdOportunidad()).stream()
            .filter(c -> c.idCita().equals(cumple.getIdCita())).findFirst().orElseThrow();
        assertThat(primero.inicioPropuesto()).isEqualTo(manana.atTime(10, 0));
        assertThat(primero.telefonoContacto()).isEqualTo("999000111");
    }

    @Test
    void elPuntajeCalculadoAMano() {
        // mismo médico 30 + teleconsulta 15 + calce 20·30/60 = 10 + antigüedad tope 15 + llegada 10·(120−60)/120 = 5
        // + consintió el mismo día 5 = 80
        assertThat(CandidatoService.calcularPuntaje(true, AlcancePreferencia.MEDICO, Modalidad.TELECONSULTA, 30, 60,
            20, 60, true)).isEqualTo(80);
        // otro médico por especialidad 10 + presencial 0 + calce 20·30/30 = 20 + 3 días + llegada inmediata 10
        assertThat(CandidatoService.calcularPuntaje(false, AlcancePreferencia.ESPECIALIDAD, Modalidad.PRESENCIAL, 30,
            30, 3, 0, false)).isEqualTo(10 + 20 + 3 + 10);
    }

    @Test
    void unPostergadoQuedaUltimoAunqueTengaMasPuntaje() {
        LocalDateTime en3 = manana.plusDays(2).atTime(9, 0);
        Cita mismoMedico = conPreferencia(medico, en3, 30, AlcancePreferencia.MEDICO, 5);
        Cita colegaEsp = conPreferencia(colega, en3.plusHours(1), 30, AlcancePreferencia.ESPECIALIDAD, 5);
        Set<Long> mias = Set.of(mismoMedico.getIdCita(), colegaEsp.getIdCita());
        assertThat(idsEnLista(mias)).containsExactly(mismoMedico.getIdCita(), colegaEsp.getIdCita());

        candidatos.postergar(op.getIdOportunidad(), mismoMedico.getIdCita(), recepcion.getIdCuenta());
        assertThat(idsEnLista(mias)).containsExactly(colegaEsp.getIdCita(), mismoMedico.getIdCita());
        Candidato ultimo = candidatos.candidatos(op.getIdOportunidad()).stream()
            .filter(c -> c.idCita().equals(mismoMedico.getIdCita())).findFirst().orElseThrow();
        assertThat(ultimo.postergado()).isTrue();
    }
}
