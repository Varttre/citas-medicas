/**
 * MODELO · entidades JPA: una clase @Entity por tabla de src/main/resources/db/migration/V1__modelo_inicial.sql
 * (25 tablas). Los nombres de tabla y columna son los del script. Los estados se guardan como
 * texto con @Enumerated(EnumType.STRING).
 *
 * La estructura la crea Flyway (db/migration), no Hibernate: spring.jpa.hibernate.ddl-auto=validate.
 */
package pe.consultorio.citas.entity;
