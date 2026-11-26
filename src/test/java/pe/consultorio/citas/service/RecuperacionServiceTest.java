package pe.consultorio.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.consultorio.citas.DatosPrueba;
import pe.consultorio.citas.PostgresTestConfig;
import pe.consultorio.citas.entity.Cuenta;
import pe.consultorio.citas.entity.NombreRol;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.repository.CuentaRepository;

/** Recuperación de contraseña asistida por el personal: enlace de un solo uso, sin correo. */
@SpringBootTest
@Import(PostgresTestConfig.class)
class RecuperacionServiceTest {

    @Autowired RecuperacionService recuperacion;
    @Autowired CuentaRepository cuentas;
    @Autowired PasswordEncoder encoder;
    @Autowired DatosPrueba datos;

    @Test
    void elEnlaceDeRecepcionCambiaLaContrasenaYSirveUnaSolaVez() {
        Cuenta c = datos.crearCuentaPaciente("olvido" + System.nanoTime() + "@prueba.pe");
        Long recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION).getIdCuenta();

        String codigo = recuperacion.generarParaPaciente(c.getPacientePropio().getIdPaciente(), recepcion);
        assertThat(recuperacion.correoDelEnlace(codigo)).hasValueSatisfying(x -> assertThat(x).contains("•••••@prueba.pe"));

        recuperacion.cambiar(codigo, "NuevaClave2026", "NuevaClave2026");
        assertThat(encoder.matches("NuevaClave2026", cuentas.findById(c.getIdCuenta()).orElseThrow().getPasswordHash()))
            .isTrue();
        assertThat(recuperacion.correoDelEnlace(codigo)).isEmpty();
        assertThatThrownBy(() -> recuperacion.cambiar(codigo, "OtraClave2026", "OtraClave2026"))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void generarOtroEnlaceAnulaElAnterior() {
        Cuenta personal = datos.crearCuentaPersonal(NombreRol.MEDICO);
        Long admin = datos.crearCuentaPersonal(NombreRol.ADMINISTRADOR).getIdCuenta();

        String primero = recuperacion.generarParaCuenta(personal.getIdCuenta(), admin);
        String segundo = recuperacion.generarParaCuenta(personal.getIdCuenta(), admin);

        assertThat(recuperacion.correoDelEnlace(primero)).isEmpty();
        assertThat(recuperacion.correoDelEnlace(segundo)).isPresent();
    }

    @Test
    void unPacienteSinCuentaPropiaNoTieneEnlaceYLasClavesDebenCoincidir() {
        Long recepcion = datos.crearCuentaPersonal(NombreRol.RECEPCION).getIdCuenta();
        Long sinCuenta = datos.crearPaciente(String.valueOf(System.nanoTime() % 100000000)).getIdPaciente();
        assertThatThrownBy(() -> recuperacion.generarParaPaciente(sinCuenta, recepcion))
            .isInstanceOf(ReglaNegocioException.class);

        Cuenta c = datos.crearCuentaPaciente("distintas" + System.nanoTime() + "@prueba.pe");
        String codigo = recuperacion.generarParaPaciente(c.getPacientePropio().getIdPaciente(), recepcion);
        assertThatThrownBy(() -> recuperacion.cambiar(codigo, "NuevaClave2026", "OtraClave2026"))
            .isInstanceOf(ReglaNegocioException.class);
        assertThat(recuperacion.correoDelEnlace(codigo)).isPresent();
    }
}
