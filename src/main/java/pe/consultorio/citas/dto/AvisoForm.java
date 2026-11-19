package pe.consultorio.citas.dto;

/** Alta o edición de un aviso del portal del paciente. El banner es la dirección de una imagen. */
public record AvisoForm(String bannerUrl, String titulo, String texto, String enlace, Boolean activo) {
}
