/**
 * CONTROLADOR (la C de MVC). Recibe la petición HTTP, valida el formulario, llama a un servicio
 * y devuelve el nombre de la vista con los datos que necesita.
 *
 * No contiene reglas de negocio ni accede a repositorios. Un subpaquete por perfil de usuario,
 * igual que las rutas (/paciente, /medico, /recepcion, /admin) y las carpetas de plantillas.
 */
package pe.consultorio.citas.controller;
