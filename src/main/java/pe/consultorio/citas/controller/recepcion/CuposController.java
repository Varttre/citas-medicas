package pe.consultorio.citas.controller.recepcion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.consultorio.citas.config.UsuarioSesion;
import pe.consultorio.citas.dto.Candidato;
import pe.consultorio.citas.dto.CupoLiberado;
import pe.consultorio.citas.entity.ResultadoLlamada;
import pe.consultorio.citas.exception.ReglaNegocioException;
import pe.consultorio.citas.service.CandidatoService;
import pe.consultorio.citas.service.ColaService;
import pe.consultorio.citas.service.LlamadaService;

/**
Cupos liberados sin cubrir: a la izquierda los bloques abiertos (los que empiezan antes, primero) y a la derecha los
candidatos del bloque elegido, en el orden en que hay que llamarlos. Cada llamada se registra; si el candidato
acepta, su misma cita se adelanta al bloque.
*/
@Controller
@RequestMapping("/recepcion/cupos")
public class CuposController {

    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    static final Map<ResultadoLlamada, String> RESULTADOS = new LinkedHashMap<>();
    static {
        RESULTADOS.put(ResultadoLlamada.ACEPTO, "Aceptó → su cita se adelanta a este horario (es la misma cita); su horario anterior se libera como otro cupo");
        RESULTADOS.put(ResultadoLlamada.RECHAZO, "Rechazó");
        RESULTADOS.put(ResultadoLlamada.NO_CONTESTO, "No contestó");
        RESULTADOS.put(ResultadoLlamada.NO_PUEDE_LLEGAR, "No puede llegar a tiempo");
        RESULTADOS.put(ResultadoLlamada.NUMERO_INVALIDO, "Número inválido");
        RESULTADOS.put(ResultadoLlamada.OTRO, "Otro");
    }

    private final ColaService colas;
    private final CandidatoService candidatos;
    private final LlamadaService llamadas;

    public CuposController(ColaService colas, CandidatoService candidatos, LlamadaService llamadas) {
        this.colas = colas;
        this.candidatos = candidatos;
        this.llamadas = llamadas;
    }

    @GetMapping
    public String lista(Model model) {
        List<CupoLiberado> cupos = colas.cuposLiberados();
        return ver(cupos, cupos.isEmpty() ? null : cupos.get(0).id(), model);
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        return ver(colas.cuposLiberados(), id, model);
    }

    private String ver(List<CupoLiberado> cupos, Long seleccionado, Model model) {
        CupoLiberado actual = cupos.stream().filter(c -> c.id().equals(seleccionado)).findFirst().orElse(null);
        model.addAttribute("cupos", cupos);
        model.addAttribute("actual", actual);
        model.addAttribute("candidatos", actual == null ? List.of() : candidatos.candidatos(actual.id()));
        model.addAttribute("resultados", RESULTADOS);
        return "recepcion/cupos";
    }

    @PostMapping("/{id}/candidatos/{idCita}/resultado")
    public String resultado(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @PathVariable Long idCita, @RequestParam ResultadoLlamada resultado,
            @RequestParam(required = false) String observacion, @RequestParam(defaultValue = "1") int posicion,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inicioPropuesto,
            RedirectAttributes flash) {
        Candidato quien = candidatos.candidatos(id).stream().filter(c -> c.idCita().equals(idCita)).findFirst()
            .orElse(null);
        try {
            llamadas.registrarCupo(id, idCita, posicion, resultado, observacion, inicioPropuesto, usuario.getIdCuenta());
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/recepcion/cupos/" + id;
        }
        if (resultado == ResultadoLlamada.ACEPTO) {
            String anterior = quien == null ? "" : " Su horario anterior (" + quien.citaActual().format(DIA_HORA)
                + ") quedó libre y, si alguien puede tomarlo, aparece en esta lista como un cupo nuevo.";
            flash.addFlashAttribute("exito", "Cupo cubierto: la cita de "
                + (quien == null ? "el paciente" : quien.paciente()) + " se adelantó al "
                + inicioPropuesto.format(DIA_HORA) + "." + anterior);
            return "redirect:/recepcion/cupos";
        }
        flash.addFlashAttribute("exito", "Llamada registrada: " + RESULTADOS.get(resultado).toLowerCase() + ".");
        return "redirect:/recepcion/cupos/" + id;
    }

    /** Deja al candidato al final de la lista de este bloque. No es una llamada ni cambia su antigüedad real. */
    @PostMapping("/{id}/candidatos/{idCita}/postergar")
    public String postergar(@AuthenticationPrincipal UsuarioSesion usuario, @PathVariable Long id,
            @PathVariable Long idCita, RedirectAttributes flash) {
        if (candidatos.postergar(id, idCita, usuario.getIdCuenta())) {
            flash.addFlashAttribute("exito", "Candidato movido al final de la lista.");
        } else {
            flash.addFlashAttribute("aviso", "Ese candidato ya es el último de la lista.");
        }
        return "redirect:/recepcion/cupos/" + id;
    }
}
