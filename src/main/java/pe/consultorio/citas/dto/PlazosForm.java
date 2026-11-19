package pe.consultorio.citas.dto;

/**
Plazos editables, en minutos positivos tal como los ve el administrador: «no asistió» se habilita X minutos después
del inicio; el paciente cancela hasta X minutos antes; la llegada se registra desde X minutos antes.
*/
public record PlazosForm(Short noAsistioDespues, Short cancelarAntes, Short llegadaAntes) {
}
