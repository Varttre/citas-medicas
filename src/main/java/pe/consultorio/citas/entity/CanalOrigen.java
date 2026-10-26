package pe.consultorio.citas.entity;

/**
Por dónde llegó la reserva. CONSULTA_MEDICA: la agendó el médico durante la atención. Sus valores
son los mismos que acepta la base de datos.
*/
public enum CanalOrigen {
    WEB, PRESENCIAL, TELEFONO, WHATSAPP, DERIVACION, CONSULTA_MEDICA
}
