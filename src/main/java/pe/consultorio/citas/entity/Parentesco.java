package pe.consultorio.citas.entity;

/** Relación del tercero con el titular de la cuenta (CUENTA_PACIENTE.parentesco, RC-046). */
public enum Parentesco {
    HIJO("Hijo/a"),
    MADRE("Madre"),
    PADRE("Padre"),
    CONYUGE("Cónyuge"),
    HERMANO("Hermano/a"),
    ABUELO("Abuelo/a"),
    NIETO("Nieto/a"),
    OTRO("Otro");

    private final String texto;

    Parentesco(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }

    /** Texto para mostrar a partir del código guardado; nulo si no hay o no se reconoce. */
    public static String texto(String codigo) {
        if (codigo == null) {
            return null;
        }
        for (Parentesco p : values()) {
            if (p.name().equals(codigo)) {
                return p.texto;
            }
        }
        return null;
    }
}
