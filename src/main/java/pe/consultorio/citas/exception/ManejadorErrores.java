package pe.consultorio.citas.exception;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
Convierte en una página de error lo que los controladores no atraparon. Nunca se muestra al usuario una traza ni un
error de base de datos: los errores inesperados se registran en el log con un código corto, y la página muestra solo
ese código para poder buscarlo después.
*/
@ControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Recurso inexistente, ruta inexistente o recurso ajeno: página 404. */
    @ExceptionHandler({ RecursoNoEncontradoException.class, NoResourceFoundException.class,
        NoHandlerFoundException.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado(Exception e) {
        log.debug("Recurso no encontrado: {}", e.getMessage());
        return "error/404";
    }

    /** Regla de negocio que el controlador no atrapó: se muestra su mensaje, que ya está redactado para el usuario. */
    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public String reglaNegocio(ReglaNegocioException e, Model model) {
        model.addAttribute("mensaje", e.getMessage());
        return "error/negocio";
    }

    /** Cualquier otro error: se registra completo en el log y la página muestra un código para encontrarlo. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String inesperado(Exception e, Model model) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;  // Los resuelve Spring Security (redirige al login o responde 403)
        }
        String idError = UUID.randomUUID().toString().substring(0, 8);
        log.error("Error no controlado [{}]", idError, e);
        model.addAttribute("idError", idError);
        return "error/500";
    }
}
