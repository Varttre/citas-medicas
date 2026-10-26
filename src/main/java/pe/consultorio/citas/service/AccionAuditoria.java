package pe.consultorio.citas.service;

/** Nombres de las acciones que se guardan en la tabla auditoria (columna accion). */
public final class AccionAuditoria {

    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    public static final String CONFIG_CAMBIO = "CONFIG_CAMBIO";
    /** El médico agendó una cita con la autorización verbal del paciente. */
    public static final String AUTORIZACION_VERBAL = "AUTORIZACION_VERBAL";
    public static final String PREFERENCIA_CAMBIO = "PREFERENCIA_CAMBIO";
    /** Un cambio de horario del médico se rechazó porque dejaba citas fuera. */
    public static final String CAMBIO_HORARIO_RECHAZADO = "CAMBIO_HORARIO_RECHAZADO";
    /** Un paciente registrado por recepción se vinculó a una cuenta web. */
    public static final String VINCULO_PACIENTE = "VINCULO_PACIENTE";
    public static final String MODO_TOTAL_ACTIVADO = "MODO_TOTAL_ACTIVADO";
    public static final String MODO_TOTAL_DESACTIVADO = "MODO_TOTAL_DESACTIVADO";
    public static final String ENTRAR_COMO = "ENTRAR_COMO";
    public static final String VOLVER_DE_ENTRAR_COMO = "VOLVER_DE_ENTRAR_COMO";

    private AccionAuditoria() {
    }
}
