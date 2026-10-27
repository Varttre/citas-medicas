package pe.consultorio.citas.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
Ficha de un paciente para recepción: datos, cuentas que agendan en su nombre, historial e indicadores.
No contiene información clínica.
*/
public record PacienteFicha(Long idPaciente, String tipoDocumento, String nroDocumento, String nombres,
        String apellidos, LocalDate fechaNacimiento, int edad, String telefono, String correo, String cuentaPropia,
        List<CuentaVinculada> cuentas, List<CitaHistorial> citas, Indicadores indicadores) {

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    /** Cuenta de un tercero que agenda por el paciente. */
    public record CuentaVinculada(Long idCuenta, String correo, LocalDateTime consentimientoEn,
            LocalDateTime revocadoEn) {
    }

    /** Una fila del historial de citas. */
    public record CitaHistorial(Long idCita, LocalDateTime inicio, String medico, String tipo, String estado) {
    }

    /** Tasas calculadas desde el historial; null cuando no hay citas para calcularlas. */
    public record Indicadores(Integer noAsistenciaPct, Integer cancelacionPct, Long diasDesdeUltimaCita) {
    }
}
