/**
 * MODELO · reglas de negocio. Aquí vive la lógica: estados de la cita, plazos, reprogramación,
 * colas de llamadas, candidatos y permisos finos.
 *
 * Cada método público que escribe es una transacción (@Transactional). Los servicios usan
 * repositorios y otros servicios; nunca devuelven vistas ni conocen HTTP.
 */
package pe.consultorio.citas.service;
