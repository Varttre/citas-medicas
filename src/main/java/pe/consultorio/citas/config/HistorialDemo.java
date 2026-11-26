package pe.consultorio.citas.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
Historial de demostración (perfil dev): corre después de DatosDemo y, si todavía no hay ninguna cita realizada,
ejecuta demo/historial-demo.sql para que las métricas, las estadísticas, las colas y las agendas tengan datos.
Las fechas del script son relativas a hoy. En una instalación real no corre.
*/
@Component
@Profile("dev")
@Order(2)
public class HistorialDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HistorialDemo.class);

    private final JdbcTemplate jdbc;

    public HistorialDemo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws IOException {
        Integer realizadas = jdbc.queryForObject("SELECT count(*) FROM cita WHERE estado = 'REALIZADA'", Integer.class);
        if (realizadas != null && realizadas > 0) {
            return;
        }
        String script = new ClassPathResource("demo/historial-demo.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(script);
        log.info("Historial de demostración cargado");
    }
}
