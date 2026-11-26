package pe.consultorio.citas.controller;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.consultorio.citas.dto.FranjaDatos;
import pe.consultorio.citas.exception.ReglaNegocioException;

/**
La grilla del horario semanal ({@code fragmentos/horario-semanal.html}), la misma en el alta del médico y en «Mi
horario». Cada día tiene su casilla «dia_{d}» (1 = lunes) y hasta dos franjas «ini_{d}_{n}» / «fin_{d}_{n}»; una
franja con las dos horas vacías se ignora, y las horas de un día sin marcar también.
*/
public final class FormularioHorario {

    public static final int FRANJAS_POR_DIA = 2;

    private FormularioHorario() {
    }

    /** Las franjas marcadas. Un error de formato se informa en el campo «horario». */
    public static List<FranjaDatos> leer(Map<String, String> campos) {
        List<FranjaDatos> lista = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            if (campos.get("dia_" + d) == null) {
                continue;
            }
            for (int n = 1; n <= FRANJAS_POR_DIA; n++) {
                String ini = campos.getOrDefault("ini_" + d + "_" + n, "");
                String fin = campos.getOrDefault("fin_" + d + "_" + n, "");
                if (ini.isBlank() && fin.isBlank()) {
                    continue;
                }
                if (ini.isBlank() || fin.isBlank()) {
                    throw new ReglaNegocioException("Cada franja necesita hora de inicio y de fin.", "horario");
                }
                try {
                    lista.add(new FranjaDatos(d, LocalTime.parse(ini), LocalTime.parse(fin)));
                } catch (DateTimeParseException e) {
                    throw new ReglaNegocioException("Hora no válida en el horario.", "horario");
                }
            }
        }
        return lista;
    }

    /** Valores para dibujar la grilla con un horario ya existente (ordenado por día y hora). */
    public static Map<String, String> valores(List<FranjaDatos> franjas) {
        Map<String, String> v = new HashMap<>();
        Map<Integer, Integer> usadas = new HashMap<>();
        franjas.stream()
            .sorted(Comparator.comparingInt(FranjaDatos::diaSemana).thenComparing(FranjaDatos::inicio))
            .forEach(f -> {
                int n = usadas.merge(f.diaSemana(), 1, Integer::sum);
                if (n <= FRANJAS_POR_DIA) {
                    v.put("dia_" + f.diaSemana(), "1");
                    v.put("ini_" + f.diaSemana() + "_" + n, f.inicio().toString());
                    v.put("fin_" + f.diaSemana() + "_" + n, f.fin().toString());
                }
            });
        return v;
    }

    /** Solo los campos de la grilla, para volver a dibujarla tal como se envió. */
    public static Map<String, String> soloGrilla(Map<String, String> campos) {
        Map<String, String> v = new HashMap<>();
        campos.forEach((k, val) -> {
            if (k.startsWith("dia_") || k.startsWith("ini_") || k.startsWith("fin_")) {
                v.put(k, val);
            }
        });
        return v;
    }
}
