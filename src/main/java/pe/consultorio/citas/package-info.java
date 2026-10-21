/**
 * Sistema de Gestión de Citas y Recuperación de Agenda.
 *
 * Arquitectura MVC en capas sobre Spring MVC:
 * petición → controller → service → repository → base de datos,
 * y la respuesta vuelve como una vista Thymeleaf (src/main/resources/templates).
 * Cada capa solo llama a la de abajo. El reparto de tareas y las convenciones están en PLAN-MVP.md.
 */
package pe.consultorio.citas;
