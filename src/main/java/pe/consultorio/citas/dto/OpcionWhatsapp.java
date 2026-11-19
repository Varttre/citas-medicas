package pe.consultorio.citas.dto;

/** Una opción de la pantalla de WhatsApp: el motivo, el mensaje que se enviará y el enlace que lo abre. */
public record OpcionWhatsapp(String motivo, String mensaje, String enlace) {
}
