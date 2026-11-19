package pe.consultorio.citas.service;

import java.util.List;

/** Cálculos estadísticos simples sobre minutos, sin Spring. */
public final class Estadistica {

    private Estadistica() {
    }

    /** Mediana: el valor central; con una cantidad par, el promedio de los dos centrales. Null si la lista está vacía. */
    public static Double mediana(List<Integer> valores) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        List<Integer> o = valores.stream().sorted().toList();
        int n = o.size();
        return n % 2 == 1 ? o.get(n / 2) : (o.get(n / 2 - 1) + o.get(n / 2)) / 2.0;
    }

    /** Percentil por rango más cercano: el valor en la posición ⌈p/100 · n⌉ de la lista ordenada. */
    public static Integer percentil(List<Integer> valores, int p) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        if (p <= 0 || p > 100) {
            throw new IllegalArgumentException("El percentil va de 1 a 100");
        }
        List<Integer> o = valores.stream().sorted().toList();
        int rango = (int) Math.ceil(p / 100.0 * o.size());
        return o.get(Math.max(rango, 1) - 1);
    }

    /** Redondea hacia arriba al múltiplo de {@code paso} (la «ranura» de la agenda). */
    public static int ranura(double minutos, int paso) {
        return (int) Math.ceil(minutos / paso) * paso;
    }
}
