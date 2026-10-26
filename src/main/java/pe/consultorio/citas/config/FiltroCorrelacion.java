package pe.consultorio.citas.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
Da a cada petición un código corto que aparece en todas las líneas del log que produce, y al terminar registra
«método ruta → estado (ms)». Con ese código se siguen juntas todas las líneas de una misma petición.
*/
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    /** Clave del MDC que usa el patrón de logback-spring.xml: %X{peticion}. */
    public static final String CLAVE_MDC = "peticion";

    private static final Logger log = LoggerFactory.getLogger(FiltroCorrelacion.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        long inicio = System.currentTimeMillis();
        MDC.put(CLAVE_MDC, UUID.randomUUID().toString().substring(0, 8));
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} → {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(),
                System.currentTimeMillis() - inicio);
            MDC.remove(CLAVE_MDC);
        }
    }

    /** Los archivos estáticos no se registran, para no llenar el log con cada CSS o imagen. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/")
            || ruta.equals("/favicon.ico");
    }
}
