/**
 * MODELO · acceso a datos. Interfaces de Spring Data JPA, una por entidad que se consulta
 * (es el patrón DAO). Consultas derivadas por nombre o @Query; sin lógica de negocio.
 *
 * Toda consulta de citas de un médico filtra por el médico de la sesión.
 */
package pe.consultorio.citas.repository;
