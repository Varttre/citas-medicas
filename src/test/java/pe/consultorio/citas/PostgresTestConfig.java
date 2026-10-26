package pe.consultorio.citas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
PostgreSQL real en un contenedor de Docker para las pruebas que usan la base. Spring toma la URL, el usuario y la
contraseña del contenedor gracias a @ServiceConnection, y Flyway crea el esquema al arrancar.

Uso: {@code @SpringBootTest} + {@code @Import(PostgresTestConfig.class)}. Necesita Docker en ejecución.
*/
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
