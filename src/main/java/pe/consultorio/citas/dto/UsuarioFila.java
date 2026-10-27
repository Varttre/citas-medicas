package pe.consultorio.citas.dto;

import java.time.LocalDateTime;

/** Una fila de la lista de usuarios del administrador. */
public record UsuarioFila(Long idCuenta, String nombre, String correo, String rol, String especialidad, String estado,
        LocalDateTime bloqueadoHasta) {

    public boolean bloqueadaTemporalmente(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }
}
