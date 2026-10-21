/**
 * Motor de disponibilidad y oferta. Es el único código «difícil» del
 * proyecto y se implementa una sola vez: lo usan el portal, recepción, el médico y la
 * disponibilidad inmediata.
 *
 * Calcula los bloques libres (horario − excepciones − citas) con Google Guava TreeRangeSet,
 * genera las horas de inicio en múltiplos del incremento y las ordena por mejor calce.
 */
package pe.consultorio.citas.service.disponibilidad;
