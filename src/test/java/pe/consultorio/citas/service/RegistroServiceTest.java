package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.dto.RegistroForm;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.Paciente;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaRepository;

/** Los cuatro casos del registro: paciente nuevo, ya vinculado, registrado por recepción con y sin coincidencia. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class RegistroServiceTest {

    @Autowired
    RegistroService registro;

    @Autowired
    DatosPrueba datos;

    @Autowired
    CuentaRepository cuentas;

    private RegistroForm form(String correo, String documento, LocalDate nacimiento, String telefono) {
        RegistroForm f = new RegistroForm();
        f.setCorreo(correo);
        f.setContrasena("Clave2026!");
        f.setConfirmacion("Clave2026!");
        f.setNroDocumento(documento);
        f.setNombres("Mario");
        f.setApellidos("Rojas");
        f.setFechaNacimiento(nacimiento);
        f.setTelefono(telefono);
        f.setAceptaTerminos(true);
        return f;
    }

    @Test
    void unPacienteNuevoCreaSuCuentaConContrasenaCifrada() {
        Long id = registro.registrar(form("Mario.Rojas@Prueba.pe ", "70000001", LocalDate.of(1985, 3, 2), "911222333"));
        Cuenta c = cuentas.findById(id).orElseThrow();
        assertThat(c.getCorreo()).isEqualTo("mario.rojas@prueba.pe");
        assertThat(c.getPasswordHash()).startsWith("$2");
        assertThat(c.getPacientePropio()).isNotNull();
        assertThatThrownBy(() -> registro.registrar(form("mario.rojas@prueba.pe", "70000009", LocalDate.of(1985, 3, 2), "911222333")))
            .hasMessage(RegistroService.CORREO_USADO);
    }

    @Test
    void unPacienteDeRecepcionSeVinculaSoloSiCoincidenFechaYTelefono() {
        Paciente p = datos.crearPaciente("70000002");
        assertThatThrownBy(() -> registro.registrar(form("otro@prueba.pe", "70000002", LocalDate.of(1991, 1, 1), "999000111")))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage(RegistroService.NO_COINCIDE);
        Long id = registro.registrar(form("vinculo@prueba.pe", "70000002", p.getFechaNacimiento(), "999 000 111"));
        assertThat(cuentas.findById(id).orElseThrow().getPacientePropio().getIdPaciente()).isEqualTo(p.getIdPaciente());
        assertThatThrownBy(() -> registro.registrar(form("tercero@prueba.pe", "70000002", p.getFechaNacimiento(), "999000111")))
            .hasMessage(RegistroService.YA_TIENE_CUENTA);
    }
}
