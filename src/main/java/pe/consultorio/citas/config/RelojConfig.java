package pe.consultorio.citas.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
Reloj único de la aplicación y activación de las tareas programadas.

Todo el código obtiene la hora con {@code LocalDateTime.now(reloj)} en lugar de {@code LocalDateTime.now()}: así la
hora es siempre la del consultorio, sin importar la zona del servidor, y las pruebas pueden fijarla con
{@code Clock.fixed(...)}.
*/
@Configuration
@EnableScheduling
public class RelojConfig {

    /** Zona horaria del consultorio. Las columnas TIMESTAMP de la base se guardan sin zona, en esta hora local. */
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Bean
    Clock reloj() {
        return Clock.system(ZONA);
    }
}
