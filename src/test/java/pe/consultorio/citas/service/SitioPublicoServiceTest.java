package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.EspecialidadPublica;
import pe.consultorio.citas.entity.Especialidad;
import pe.consultorio.citas.entity.Medico;
import pe.consultorio.citas.repository.EspecialidadRepository;
import pe.consultorio.citas.repository.MedicoRepository;

/** La página pública muestra solo especialidades activas y, en cada una, solo sus médicos activos. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class SitioPublicoServiceTest {

    @Autowired SitioPublicoService sitio;
    @Autowired DatosPrueba datos;
    @Autowired EspecialidadRepository especialidades;
    @Autowired MedicoRepository medicos;

    @Test
    void muestraEspecialidadesActivasConSusMedicosActivos() {
        Especialidad activa = datos.crearEspecialidad("Neurología");
        datos.crearMedico("Visible", activa);
        Medico retirado = datos.crearMedico("Retirado", activa);
        retirado.setActivo(false);
        medicos.save(retirado);
        Especialidad inactiva = datos.crearEspecialidad("Oculta");
        inactiva.setActivo(false);
        especialidades.save(inactiva);

        var lista = sitio.especialidades();

        EspecialidadPublica e = lista.stream().filter(x -> x.nombre().equals(activa.getNombre())).findFirst().orElseThrow();
        assertThat(e.medicos()).containsExactly("Visible Prueba");
        assertThat(lista).noneMatch(x -> x.nombre().equals(inactiva.getNombre()));
    }
}
