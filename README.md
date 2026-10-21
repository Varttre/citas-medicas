# citas-medicas

Sistema de gestión de citas y recuperación de agenda para un consultorio médico. Aplicación web en Java.

- **Stack:** Java 21 · Spring Boot 3.5 · Maven · Thymeleaf + Bootstrap 5 · PostgreSQL 16 (extensión `btree_gist`) ·
  Spring Data JPA · Flyway · Spring Security · Spring Session JDBC · Guava · Apache POI · Logback · JUnit 5 · Testcontainers.
- **Arquitectura:** MVC en capas sobre Spring MVC: controlador → servicio → repositorio → entidad. Un paquete por capa.
- **Plan de trabajo y reparto de tareas:** [`PLAN-MVP.md`](PLAN-MVP.md).
- **Diseño de las pantallas:** [`prototipo/`](prototipo/) (HTML navegable; abrir `prototipo/index.html` en el navegador).
- **Esquema de la base de datos:** [`V1__modelo_inicial.sql`](src/main/resources/db/migration/V1__modelo_inicial.sql).

## Requisitos

- JDK 21 o superior. No hace falta instalar Maven: se usa el wrapper (`mvnw`).
- Docker Desktop: para la base de datos local y para las pruebas de integración (Testcontainers).

## Base de datos local

```bash
docker run -d --name citas-pg -p 5432:5432 \
  -e POSTGRES_DB=citas_medicas -e POSTGRES_USER=citas -e POSTGRES_PASSWORD=citas \
  postgres:16-alpine
```

La aplicación se conecta con esos valores por defecto; se cambian con las variables `DB_URL`, `DB_USER` y `DB_PASSWORD`.
Flyway crea el esquema al arrancar. Para empezar de cero: `docker rm -f citas-pg` y volver a crear el contenedor.

## Comandos

```bash
./mvnw test                                                    # compila y ejecuta las pruebas
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # arranca en http://localhost:8080 con datos de demostración
```

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`. Los usuarios de prueba del perfil `dev` están en `PLAN-MVP.md` (sección 13.6).
