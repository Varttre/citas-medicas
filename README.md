# citas-medicas

Sistema de gestión de citas y recuperación de agenda para un consultorio médico. Aplicación web en Java.

- **Stack:** Java 21 · Spring Boot 3.5 · Maven · Thymeleaf + Bootstrap 5 · PostgreSQL 16 (extensión `btree_gist`) ·
  Spring Data JPA · Flyway · Spring Security · Spring Session JDBC · Guava · Apache POI · Logback · JUnit 5 · Testcontainers.
- **Arquitectura:** MVC en capas sobre Spring MVC: controlador → servicio → repositorio → entidad. Un paquete por capa.
- **Plan de trabajo y reparto de tareas:** [`PLAN-MVP.md`](PLAN-MVP.md).
- **Diseño de las pantallas:** [`prototipo/`](prototipo/) (HTML navegable; abrir `prototipo/index.html` en el navegador).
- **Esquema de la base de datos:** [`V1__modelo_inicial.sql`](src/main/resources/db/migration/V1__modelo_inicial.sql).

## Requisitos

- JDK 21 o superior (`java -version` debe decir 21 o más). No hace falta instalar Maven: se usa el wrapper (`mvnw`).
- Docker Desktop abierto: para la base de datos local y para las pruebas (Testcontainers).

## Levantar el programa (3 pasos)

Se usan los puertos **5434** (base de datos) y **18080** (web) para no chocar con un PostgreSQL instalado en Windows
(que suele ocupar el 5432) ni con otros programas en el 8080.

**1. Crear la base de datos** (solo la primera vez):

```bash
docker run -d --name citas-pg -p 5434:5432 -e POSTGRES_DB=citas_medicas -e POSTGRES_USER=citas -e POSTGRES_PASSWORD=citas postgres:16-alpine
```

Las veces siguientes basta con `docker start citas-pg` (o encenderlo desde Docker Desktop).

**2. Arrancar la aplicación** desde la carpeta del proyecto (la que tiene `pom.xml`).

En PowerShell (Windows):

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5434/citas_medicas"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=18080"
```

En CMD (Windows):

```bat
set DB_URL=jdbc:postgresql://localhost:5434/citas_medicas
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=18080
```

En Linux o macOS:

```bash
DB_URL=jdbc:postgresql://localhost:5434/citas_medicas ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=18080
```

Está listo cuando la consola dice `Started CitasMedicasApplication`. La primera vez tarda más porque descarga las
dependencias. Flyway crea las tablas solo, y el perfil `dev` carga usuarios y datos de demostración.

**3. Abrir** <http://localhost:18080> y entrar con una de estas cuentas (contraseña de todas: `Demo2026!`):

| Perfil | Correo |
| --- | --- |
| Administrador | `admin@consultorio.pe` |
| Recepción | `recepcion@consultorio.pe` |
| Médico | `medico1@` y `medico4@` (Cardiología) · `medico2@` y `medico5@` (Medicina General) · `medico3@` (Pediatría) · `medico6@` (Dermatología) · `medico7@` (Traumatología), todos `@consultorio.pe` |
| Paciente | `paciente@consultorio.pe` |

Para detener la aplicación: `Ctrl + C` en la consola.

Todos los médicos de demostración atienden **todos los días hasta las 23:00**. Dermatología y Traumatología no tienen
citas (agendas vacías para reservar sin choques). Hay 15 pacientes en espera (3 por médico de Cardiología, Medicina
General y Pediatría) con «Acepto ser llamado»: si se cancela una cita, el cupo liberado siempre tiene candidatos.

**Volver a empezar con datos de hoy (Windows):** doble clic en `reiniciar-demo.bat`. Borra `citas-pg`, la crea de nuevo
y arranca la aplicación; los datos de demostración quedan relativos a ese momento. Úsalo antes de cada práctica y unos
30 minutos antes de exponer. Antes cierra la aplicación si está abierta.

## Pruebas

```bash
.\mvnw.cmd test        # Windows  (en Linux o macOS: ./mvnw test)
```

Las pruebas levantan su propia base de datos con Testcontainers: solo necesitan Docker abierto, no `citas-pg`.

## Problemas frecuentes

| Lo que aparece | Por qué | Qué hacer |
| --- | --- | --- |
| `la autentificación password falló para el usuario «citas»` | La aplicación se conectó a otro PostgreSQL (el de Windows, en el 5432), no al contenedor | Revisar que `DB_URL` apunte al puerto **5434** y que se haya puesto en la **misma** consola donde se arranca |
| `Connection refused` o `Unable to obtain connection` | El contenedor está apagado | `docker start citas-pg` |
| `Port 18080 was already in use` | La aplicación ya está abierta en otra consola | Cerrar la otra o cambiar `--server.port` por otro número |
| `port is already allocated` al crear el contenedor | Ya existe algo en el 5434 | Usar otro puerto en ambos lados, p. ej. `-p 5435:5432` y `localhost:5435` en `DB_URL` |
| `release version 21 not supported` | El JDK es anterior al 21 | Instalar JDK 21 y revisar `JAVA_HOME` |
| Una cuenta de demostración no deja entrar | Se bloquea 15 minutos tras 5 intentos fallidos | Esperar o desbloquearla desde `admin@consultorio.pe` → Usuarios |

**Empezar de cero** (borra todos los datos): `docker rm -f citas-pg` y repetir el paso 1.
