package pe.consultorio.citas.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Prueba sin Spring del reconocimiento del error de solape de PostgreSQL. */
class ErroresBaseDatosTest {

    @Test
    void reconoceElSolapeAunqueVengaEnvueltoEnDosCausas() {
        SQLException original = new SQLException("x", "23P01");
        RuntimeException envuelto = new RuntimeException("capa 2", new IllegalStateException("capa 1", original));

        assertThat(ErroresBaseDatos.esSolape(envuelto)).isTrue();
    }

    @Test
    void noConfundeOtrasViolacionesConUnSolape() {
        SQLException unico = new SQLException("duplicado", "23505");

        assertThat(ErroresBaseDatos.esSolape(new RuntimeException(unico))).isFalse();
        assertThat(ErroresBaseDatos.esSolape(new RuntimeException("sin causa"))).isFalse();
        assertThat(ErroresBaseDatos.esSolape(null)).isFalse();
    }
}
