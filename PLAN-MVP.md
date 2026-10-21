# Plan de trabajo del MVP — citas-medicas

Reparto del trabajo que falta para tener el **producto mínimo viable** del sistema de gestión de citas, en cuatro
paquetes para cuatro personas. Cada tarea dice **qué se espera**, **con qué valores y de dónde salen**, **qué archivos
produce** y **cuándo se considera terminada**. Todo lo que hace falta para trabajar está en este archivo, en el código
del repositorio y en el prototipo HTML (`prototipo/`).

| Paquete | Peso | Tema | Dificultad |
| --- | --- | --- | --- |
| **A** | 40 % | Núcleo: base de datos en Java, motor de horarios, reserva, estados, cupos liberados, horario del médico | La más alta: lógica de negocio y concurrencia |
| **B** | 25 % | Pantallas de recepción (agenda, nueva cita, tres colas de llamadas, pacientes) y agenda del médico | Media: muchas pantallas sobre servicios de A |
| **C** | 20 % | Acceso (login, registro, recuperar contraseña), portal del paciente e inicio del médico | Media-baja |
| **D** | 15 % | Plantilla visual común, datos iniciales, configuración y usuarios del administrador, superadministrador, reportes con Excel | La más baja: CRUD y consultas simples |

Responsables: **A** Aarón Vargas · **B** ____________ · **C** ____________ · **D** ____________

---

## Índice

1. [Cómo se trabaja con este plan](#1-cómo-se-trabaja-con-este-plan)
2. [El sistema en una página](#2-el-sistema-en-una-página)
3. [Qué hay hoy en el código](#3-qué-hay-hoy-en-el-código)
4. [Alcance del MVP](#4-alcance-del-mvp)
5. [Entorno de cada integrante](#5-entorno-de-cada-integrante)
6. [Convenciones comunes](#6-convenciones-comunes)
7. [Contratos compartidos (clases que unos crean y otros usan)](#7-contratos-compartidos)
8. [Orden de integración](#8-orden-de-integración)
9. [Paquete A — Aarón](#9-paquete-a--40-)
10. [Paquete B — por asignar](#10-paquete-b--25-)
11. [Paquete C — por asignar](#11-paquete-c--20-)
12. [Paquete D — por asignar](#12-paquete-d--15-)
13. [Datos de referencia](#13-datos-de-referencia)
14. [Lista de revisión antes de fusionar un PR](#14-lista-de-revisión-antes-de-fusionar-un-pr)

---

## 1. Cómo se trabaja con este plan

### 1.1 Git y GitHub

- **Una rama por tarea**, con el código de la tarea en el nombre: `a1-entidades`, `c2-registro`, `d4-usuarios`.
- **Nadie hace push a `main`.** Cada tarea termina en un *pull request* (PR) hacia `main`; lo revisa y lo fusiona otro
  integrante distinto del autor (quién exactamente, hasta que se decida).
- El PR se fusiona con **«Create a merge commit»** (no *squash*): así se conservan los commits de cada persona y el
  historial muestra quién hizo qué.
- Commits pequeños y en español: `A3: genera inicios en múltiplos del incremento`.
- Antes de abrir el PR: `git pull origin main` en tu rama, resolver conflictos y volver a correr las pruebas.
- Título del PR: `A3 · Motor de disponibilidad`. En la descripción, la lista de «Hecho cuando» de la tarea marcada, y
  capturas si la tarea tiene pantallas.

### 1.2 Definición de «terminado» (vale para todas las tareas)

1. `./mvnw test` pasa (en Windows: `mvnw.cmd test`).
2. La aplicación arranca con `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` y la pantalla de la tarea funciona
   con los usuarios de prueba (§13.6).
3. El PR solo toca los archivos de su tarea. Excepción permitida: añadir métodos **al final** de un repositorio
   (`CitaRepository`, etc.) o una entrada en `SeguridadConfig`.
4. Los comentarios explican el código por sí mismos. **No se citan documentos externos**: nada de «ver sección 8»,
   «RN-12» ni rutas a archivos que no estén en el repositorio.
5. Se cumple lo de la sección «Hecho cuando» de la tarea.

### 1.3 Cómo leer cada tarea

```
### X9 · Nombre (puntos)
Depende de: tareas que deben estar fusionadas antes
Qué se espera: el comportamiento, en palabras
Entradas: valores, variables y de dónde salen (tabla, parámetro, sesión, formulario)
Salida: archivos que se crean o modifican y lo que devuelven
Hecho cuando: pruebas y comprobaciones concretas
```

Los **puntos** miden el esfuerzo relativo. Total del MVP: **131 puntos** (A 50 · B 31 · C 28 · D 22).

---

## 2. El sistema en una página

Software web para **un consultorio médico** (una sola sede) que agenda citas y **recupera los horarios que se pierden**
cuando alguien cancela. Cuatro perfiles de uso y uno técnico:

| Perfil | Qué hace | Rutas |
| --- | --- | --- |
| **Paciente** | Se registra, agenda citas por web para sí mismo o para otra persona (un hijo, un padre), ve sus citas, las reprograma o cancela, acepta que lo llamen si se libera un horario antes | `/paciente/**` |
| **Médico** | Ve solo **su** agenda, registra inicio y fin de la atención, configura su horario, indica una siguiente cita | `/medico/**` |
| **Recepción** | Ve la agenda de todos, registra llegadas, agenda por teléfono o en persona, **confirma citas por teléfono**, llama a candidatos cuando se libera un horario | `/recepcion/**` |
| **Administrador** | Todo lo de recepción + configuración, usuarios y reportes | `/recepcion/**` y `/admin/**` |
| **Superadministrador** | Cuenta técnica (desarrollo y soporte). Por defecto trabaja en **modo limitado**, igual que un administrador. En **modo total** (se activa a propósito, con contraseña) puede además **entrar como cualquier cuenta** y gestionar a los demás superadministradores. Tarea D7 | Las del administrador; el resto, entrando como otra cuenta |

Ideas que **no se pueden romper** (están por todo el código):

1. **Los horarios disponibles no se guardan: se calculan.** Libre = horario del médico − excepciones − citas. Se calcula
   con Guava `TreeRangeSet` en un solo lugar (`MotorDisponibilidad`).
2. **Todo es múltiplo de 15 minutos** (el «incremento»): inicios, fines, duraciones, franjas. Los selectores de hora solo
   ofrecen esos valores.
3. **Dos citas del mismo médico no pueden solaparse, y eso lo garantiza PostgreSQL**, no el código: la restricción
   `excl_cita_solape` rechaza la segunda con el error SQL `23P01`. El código **no** «verifica y luego inserta»: inserta,
   y si la base rechaza, muestra «Ese horario ya fue ocupado» y vuelve a la lista de horarios **sin perder lo que el
   usuario ya había llenado**.
4. **Confirmar una cita solo es posible desde la cola de llamadas de recepción**, con la llamada registrada. El paciente
   puede cancelar, pero no confirmar.
5. **Reprogramar no crea otra cita**: la misma fila (mismo `id_cita`) cambia de horario, vuelve a `PROGRAMADA` y pierde
   la confirmación. Se registra un evento `REPROGRAMACION`.
6. **Cada cambio de estado deja un evento** en `cita_evento` (quién, cuándo, motivo). Eventos, llamadas y auditoría
   **nunca se editan ni se borran**.
7. **Un médico solo ve sus citas porque la consulta filtra por el médico de la sesión**, no porque la pantalla esconda
   un botón. El `id_medico` nunca se toma de la URL.
8. **El sistema recomienda, la persona decide**: el orden de horarios y la lista de candidatos son sugerencias.
9. **Cuenta ≠ paciente.** Registrarse crea la cuenta y su *paciente propio*. Una cuenta puede agendar para otros
   pacientes (terceros). El teléfono de contacto de una cita es el del paciente propio de la cuenta que la agendó; si la
   agendó recepción sin cuenta, el del paciente.

### 2.1 Vida de una cita

```
                 ┌────────────── REPROGRAMACIÓN (evento: mismo id, vuelve a PROGRAMADA) ──────────┐
                 ▼                                                                                  │
            PROGRAMADA ──(solo cola telefónica)──▶ CONFIRMADA                                       │
             │   │   │                              │   │   │                                       │
             │   │   └──────────────┐   ┌───────────┘   │   └──▶ NO_ASISTIO                         │
             │   │                  ▼   ▼               │                                           │
             │   └──▶ NO_ASISTIO   CANCELADA_POR_PACIENTE / CANCELADA_POR_CLINICA ──▶ horario liberado
             │                                          │
             └──────(recepción marca llegada)──▶ ASISTIO ◀──────────┘
                                                  │    └──(se retiró, motivo obligatorio)──▶ NO_ASISTIO
                                     (médico registra fin de la atención)
                                                  ▼
                                              REALIZADA
```

Las transiciones, quién las hace y en qué plazo están en §13.2.

### 2.2 Recuperación de horarios (la razón de ser del sistema)

1. Se cancela o reprograma una cita → el horario queda libre.
2. El sistema calcula el **bloque libre completo** que contiene ese horario (si 11:30–12:00 ya estaba libre y se cancela
   12:00–12:30, el bloque es 11:30–12:30) y lo guarda como una **oportunidad** (`oportunidad`, estado `ABIERTA`).
3. Busca **candidatos**: pacientes con una cita futura que aceptaron que los llamen para adelantarla
   (`preferencia_disponibilidad`) y que cumplen ciertas condiciones. Los ordena por puntaje.
4. Recepción los llama en orden y registra el resultado. Si uno acepta, **su cita existente se mueve** al nuevo horario
   (mismo id) y la oportunidad queda `CUBIERTA`. Lo que sobra del bloque vuelve a ofrecerse.

---

## 3. Qué hay hoy en el código

| Archivo | Estado |
| --- | --- |
| `pom.xml` | Completo: Spring Boot 3.5.16, Java 21, web, Thymeleaf, validation, security, Spring Session JDBC, JPA, PostgreSQL, Flyway, Guava 33.7.1, POI 5.5.1, Testcontainers |
| `application.properties` | Conexión a PostgreSQL por variables `DB_URL`, `DB_USER`, `DB_PASSWORD`; `ddl-auto=validate`; `open-in-view=false` |
| `config/SeguridadConfig.java` | Rutas por rol, `formLogin` con `/login` (la página aún no existe), BCrypt |
| `controller/publico/InicioController.java` + test | Página pública mínima |
| `src/main/resources/db/migration/V1__modelo_inicial.sql` | Completo: extensión `btree_gist`, 25 tablas en minúsculas, 58 CHECK, 2 EXCLUDE, 39 FK y comentario en cada columna. **No se edita**: un cambio de esquema va en `V3__…` |
| Paquetes `controller/{publico,paciente,medico,recepcion,admin}`, `service`, `service/disponibilidad`, `repository`, `entity`, `dto`, `exception` | **Vacíos** |
| `templates/{publico,paciente,medico,recepcion,admin,fragmentos}` | Vacíos salvo `publico/inicio.html` |
| `prototipo/` | Las 33 pantallas HTML del diseño (índice en `prototipo/index.html`) con `assets/app.js` y `assets/app.css` |

**Falta todo lo demás**: entidades, repositorios, servicios, controladores y vistas. Las pantallas ya están dibujadas en
`prototipo/` (HTML + Bootstrap 5): **cada plantilla Thymeleaf se construye copiando la estructura de su pantalla del
prototipo** y reemplazando los datos fijos por `th:text`, `th:each`, etc.

---

## 4. Alcance del MVP

### 4.1 Entra

- Login con bloqueo por intentos, registro con paciente propio, recuperación de contraseña con token de un solo uso,
  perfil del paciente.
- Configuración del administrador (tipos de consulta, parámetros, plazos, especialidades, avisos) y alta de usuarios.
- Superadministrador con modo limitado (por defecto) y modo total: entrar como cualquier cuenta y gestionar superadministradores.
- Horario del médico: versiones con vigencia, franjas, excepciones y duraciones por tipo.
- Motor de disponibilidad con Guava y bloqueo temporal de 5 minutos.
- Reserva desde el portal, desde recepción y desde la consulta del médico; estados con plazos; reprogramación desde el
  portal del paciente y desde recepción.
- Citas indicadas por el médico.
- Disponibilidad inmediata: oportunidades, candidatos con puntaje y llamadas.
- Tres colas de recepción: confirmaciones, cupos liberados y citas pendientes.
- Portal del paciente: inicio, nueva cita, mis citas, avisos, WhatsApp.
- Inicio y agenda del médico; inicio, agenda y pacientes de recepción.
- Reportes con **4 métricas** y exportación a Excel (Apache POI); estadísticas básicas del médico.
- Logs técnicos con Logback.

### 4.2 No entra en el MVP (se hace después si hay tiempo)

| Fuera del MVP | Qué se hace en su lugar |
| --- | --- |
| Política de protección de bloques `BLANDA` y `DURA` | El motor ordena solo por mejor calce (equivale a `NUNCA`). Tarea opcional A12 |
| Sugerencia de duración al médico (aceptar / rechazar / recordar después) | El médico cambia sus duraciones a mano en «Mi horario» |
| Otras métricas del tablero: tiempo para llenar un horario liberado, % de ofertas de cupo aceptadas, posición del candidato que aceptó, duración real contra prevista, minutos perdidos en residuos | Se entregan 4 métricas (D5) |
| Envío real del correo de recuperación | El enlace se escribe en el log (perfil `dev`) |
| Horario de un médico editado por el administrador | Lo edita el propio médico |
| Contadores en la barra lateral y vista semanal del médico | Solo vista día |

---

## 5. Entorno de cada integrante

El repositorio ya trae el esqueleto Maven, el prototipo (`prototipo/`) y el esquema de la base (`V1__modelo_inicial.sql`).
Cada integrante clona, prepara esto y crea su rama.

- **JDK 21** o superior. No hace falta instalar Maven (se usa `mvnw`).
- **Docker Desktop** (para la base local y para las pruebas con Testcontainers).
- Base de datos local:

```bash
docker run -d --name citas-pg -p 5432:5432 \
  -e POSTGRES_DB=citas_medicas -e POSTGRES_USER=citas -e POSTGRES_PASSWORD=citas \
  postgres:16-alpine
```

- Arrancar con datos de prueba: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → http://localhost:8080
- Borrar la base y empezar de cero: `docker rm -f citas-pg` y volver a crearla. **Nunca se edita una migración ya
  fusionada**: si falta algo en el esquema, se crea `V3__…sql` y se avisa al grupo.

---

## 6. Convenciones comunes

### 6.1 Capas (de arriba abajo; nunca al revés)

```
controller → service → repository → entity
```

| Capa | Va | No va |
| --- | --- | --- |
| `controller/<perfil>/` | Rutas, `@Valid` del formulario, **una** llamada a un servicio, nombre de la vista, mensajes flash | Reglas de negocio, repositorios, `@Transactional` |
| `dto/` | Formularios (`…Form`, con anotaciones de validación) y datos que muestra la vista (`record`) | Entidades |
| `service/` | Reglas, cambios de estado, plazos, permisos finos, eventos y auditoría. `@Transactional` en cada método que escribe | HTML, `HttpServletRequest`, nombres de vista |
| `service/disponibilidad/` | El motor (Java puro) y los servicios que lo alimentan | — |
| `repository/` | `interface XRepository extends JpaRepository<X, Id>`, consultas por nombre y `@Query` | Lógica |
| `entity/` | Una clase `@Entity` por tabla + los `enum` de estados | Lógica de pantalla |
| `templates/<perfil>/` | Plantillas Thymeleaf copiadas del prototipo | Lógica: la vista muestra lo que trae el DTO |

**Las vistas no reciben entidades** (con `open-in-view=false` fallan las cargas perezosas): el controlador pasa
`record`s de `dto/`.

### 6.2 Nombres

| Elemento | Convención | Ejemplo |
| --- | --- | --- |
| Paquete base | `pe.consultorio.citas` | `pe.consultorio.citas.service.disponibilidad` |
| Clases | Sufijo de la capa, en español | `AgendaController`, `ReservaService`, `CitaRepository` |
| Entidad | Singular, `@Table(name = "<tabla en minúsculas>")` | `Cita` → `cita`; `CitaEvento` → `cita_evento` |
| Atributo | camelCase con `@Column(name = "<columna>")` | `idCuentaTitular` → `id_cuenta_titular` |
| Enums | Mismos valores que los CHECK de la tabla, sin tildes | `EstadoCita.NO_ASISTIO` |
| Rutas | `/<perfil>/<sección>` | `/recepcion/confirmaciones` |
| Plantilla | Nombre de la pantalla del prototipo sin el prefijo del perfil | `prototipo/paciente-mis-citas.html` → `templates/paciente/mis-citas.html` |
| Migraciones | `V<n>__<descripcion>.sql` | `V2__datos_iniciales.sql` |
| Pruebas | Mismo paquete + `Test` | `MotorDisponibilidadTest` |

### 6.3 Tipos Java según el tipo de la columna en `V1__modelo_inicial.sql`

`ddl-auto=validate` compara cada atributo con su columna y **no arranca** si el tipo no coincide.

| SQL | Java |
| --- | --- |
| `BIGINT` | `Long` |
| `SMALLINT` | `Short` (**no** `Integer`: falla la validación) |
| `VARCHAR(n)` | `String` con `@Column(length = n)` |
| `VARCHAR` con valores de un CHECK | `enum` con `@Enumerated(EnumType.STRING)` |
| `TEXT` | `String` |
| `CHAR(36)` (solo `token_recuperacion.jti`) | `String` con `@JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36)` |
| `BOOLEAN` | `boolean` (o `Boolean` si admite NULL) |
| `DATE` / `TIME` / `TIMESTAMP` | `LocalDate` / `LocalTime` / `LocalDateTime` |
| `GENERATED ALWAYS AS IDENTITY` | `@GeneratedValue(strategy = GenerationType.IDENTITY)` |

### 6.4 Hora actual

**Nunca** `LocalDateTime.now()` a secas. Se inyecta el `Clock` del sistema (§7.2) y se usa
`LocalDateTime.now(reloj)`. Así las pruebas fijan la hora con `Clock.fixed(...)`.

### 6.5 Usuario de la sesión

En cualquier controlador:

```java
@GetMapping("/paciente/inicio")
public String inicio(@AuthenticationPrincipal UsuarioSesion usuario, Model model) {
    model.addAttribute("datos", portalService.inicio(usuario.getIdCuenta()));
    return "paciente/inicio";
}
```

Los servicios reciben **ids** (`Long idCuenta`), nunca el objeto de sesión ni la petición HTTP.

### 6.6 Mensajes al usuario

- Éxito, error y aviso se pasan como atributos *flash* `exito`, `error`, `aviso` en un `redirect:`; el fragmento
  `fragmentos/base :: mensajes` (D1) los muestra.
- Todas las excepciones de negocio heredan de `ReglaNegocioException` y su **mensaje ya está listo para el usuario**, en
  español. El controlador hace `catch (ReglaNegocioException e)` → `flash("error", e.getMessage())`.
- Nunca se muestra un error de base de datos ni una traza.

### 6.7 Objetos en sesión

La sesión se guarda en PostgreSQL (Spring Session JDBC), así que **todo lo que se guarda en sesión debe implementar
`Serializable`** y no contener entidades: `UsuarioSesion` y los formularios de los asistentes de varios pasos
(`@SessionAttributes`).

### 6.8 Pruebas

| Qué | Cómo |
| --- | --- |
| Motor, puntaje, estadísticas (Java puro) | JUnit 5 sin Spring |
| Controladores (rutas y permisos) | `@WebMvcTest` + `spring-security-test` (ejemplo: `InicioControllerTest`) |
| Servicios con base de datos | `@SpringBootTest` + `@Import(PostgresTestConfig.class)` (Testcontainers, PostgreSQL real; necesita Docker) |

Nunca una base en memoria (H2): las restricciones de exclusión solo existen en PostgreSQL.

---

## 7. Contratos compartidos

Son las clases que **una** persona crea y **todas** usan. Las firmas son fijas: si alguien necesita cambiarlas, lo
acuerda con quien creó la clase y se avisa al grupo. Para que nadie espere a nadie, **A1 crea las clases de servicio de A como «cáscaras»**:
la firma completa y el cuerpo `throw new UnsupportedOperationException("Pendiente: A5");`. Así B, C y D compilan
desde el primer día, y cada tarea de A rellena su cáscara.

### 7.1 Enums (A1, paquete `entity`)

```java
public enum NombreRol { PACIENTE, MEDICO, RECEPCION, ADMINISTRADOR, SUPERADMIN }
public enum EstadoCuenta { ACTIVA, BLOQUEADA, BAJA }
public enum TipoDocumento { DNI, CE, PASAPORTE }
public enum PoliticaProteccion { NUNCA, BLANDA, DURA }
public enum ModalidadPermitida { PRESENCIAL, TELECONSULTA, AMBAS }
public enum Modalidad { PRESENCIAL, TELECONSULTA }
public enum CanalOrigen { WEB, PRESENCIAL, TELEFONO, WHATSAPP, DERIVACION, CONSULTA_MEDICA }
public enum EstadoCita { PROGRAMADA, CONFIRMADA, ASISTIO, REALIZADA, NO_ASISTIO,
                         CANCELADA_POR_PACIENTE, CANCELADA_POR_CLINICA }
public enum Transicion { NO_ASISTIO, RETIRO, CANCELADA_POR_PACIENTE, CANCELADA_POR_CLINICA,
                         CONFIRMADA, ASISTIO, REALIZADA }
public enum TipoEvento { CREACION, CAMBIO_ESTADO, REPROGRAMACION, AJUSTE_DURACION,
                         LLEGADA, INICIO_ATENCION, FIN_ATENCION }
public enum EstadoIndicacion { PENDIENTE, AGENDADA, VENCIDA, DESCARTADA }
public enum AlcancePreferencia { MEDICO, ESPECIALIDAD }
public enum EstadoOportunidad { ABIERTA, CUBIERTA, EXPIRADA, REEMPLAZADA }
public enum TipoCola { CONFIRMACION, CUPO_LIBERADO, CITA_PENDIENTE }
public enum ResultadoLlamada { CONFIRMO, NO_CONTESTO, CANCELO, REPROGRAMO, NUMERO_INVALIDO,
                              ACEPTO, RECHAZO, NO_PUEDE_LLEGAR, OTRO, AGENDO, NO_LE_INTERESA }
```

### 7.2 Infraestructura (A2)

```java
// config/RelojConfig.java
@Bean Clock reloj() { return Clock.system(ZoneId.of("America/Lima")); }

// exception/
public class ReglaNegocioException extends RuntimeException { public ReglaNegocioException(String mensajeParaUsuario) }
public class HorarioOcupadoException extends ReglaNegocioException      // "Ese horario ya fue ocupado. Elige otro."
public class PlazoVencidoException extends ReglaNegocioException
public class RecursoNoEncontradoException extends RuntimeException      // se muestra como 404
public final class ErroresBaseDatos { public static boolean esSolape(Throwable e) }  // SQLState 23P01

// service/AuditoriaService.java
public void registrar(Long idCuenta, String accion, String entidad, String idRegistro, String detalle, String motivo)
// service/AccionAuditoria.java — constantes String
LOGIN_OK, LOGIN_FALLIDO, CUENTA_BLOQUEADA, CONFIG_CAMBIO, AUTORIZACION_VERBAL,
PREFERENCIA_CAMBIO, CAMBIO_HORARIO_RECHAZADO, VINCULO_PACIENTE,
MODO_TOTAL_ACTIVADO, MODO_TOTAL_DESACTIVADO, ENTRAR_COMO, VOLVER_DE_ENTRAR_COMO
```

### 7.3 Sesión (C1, paquete `config`)

```java
public class UsuarioSesion implements UserDetails {   // Serializable: solo tipos simples
    Long getIdCuenta();
    NombreRol getRol();
    Long getIdMedico();          // null si no es médico
    Long getIdPacientePropio();  // null si no es paciente
    String getNombreMostrado();  // paciente: nombres del paciente propio; médico: nombres + apellidos; personal: nombre_visible
    String getUsername();        // el correo
}
```

### 7.4 Configuración (D2)

```java
// service/ConfiguracionService.java
ParametroConsultorio parametros();               // la fila id_parametro = 1
PlazoTransicion plazo(Transicion t);
List<TipoConsulta> tiposActivos();
List<TipoConsulta> tiposWeb();                    // activos y reservable_web
List<Especialidad> especialidadesActivas();
int incremento();                                 // parametros().getIncrementoMin()
int huecoMinimoUtil();                            // la menor duracion_defecto entre los tipos activos
```

### 7.5 Motor y reserva (A3 a A10)

```java
// service/disponibilidad/
public record Oferta(LocalDateTime inicio, LocalDateTime fin, LocalDateTime bloqueInicio,
                     LocalDateTime bloqueFin, int residuoMin, Calce calce, boolean protegida) {}
public enum Calce { PERFECTO, VENDIBLE, BASURA }
public record OfertaDia(LocalDate fecha, List<Oferta> ofertas) {}
public final class Rejilla {
    static boolean enRejilla(LocalDateTime t, int incremento);
    static LocalDateTime redondearArriba(LocalDateTime t, int incremento);
    static List<LocalTime> horasDelDia(LocalTime desde, LocalTime hasta, int incremento); // para los <select>
}

DisponibilidadService:
    List<OfertaDia> ofertasProximas(Long idMedico, int duracionMin, LocalDate desde, Long idCuentaQuePide); // 3 días
    List<Oferta> ofertasDelDia(Long idMedico, int duracionMin, LocalDate fecha, Long idCuentaQuePide);
    RangeSet<LocalDateTime> bloquesLibresDelDia(Long idMedico, LocalDate fecha);

BloqueoTemporalService:
    BloqueoTemporal tomar(Long idCuenta, Long idMedico, LocalDateTime inicio, LocalDateTime fin);
    Optional<BloqueoTemporal> vigenteDe(Long idCuenta);
    long segundosRestantes(BloqueoTemporal b);
    void liberar(Long idCuenta);

// dto/
public record PreferenciaDatos(AlcancePreferencia alcance, int minLlegadaMin, int diasPrevios) implements Serializable {}
public record NuevaCitaComando(Long idPaciente, Long idCuentaTitular, Long idMedico, Short idEspecialidad,
        Short idTipoConsulta, LocalDateTime inicio, int duracionMin, Modalidad modalidad, CanalOrigen canal,
        Long idCuentaActor, Long idIndicacion, PreferenciaDatos preferencia, String indicacionesAdmin,
        boolean autorizacionVerbal) {}
public record VentanaPlazo(LocalDateTime desde, LocalDateTime hasta) { boolean permite(LocalDateTime ahora); }
public record IndicacionComando(Short idTipoConsulta, int duracionMin, LocalDate fechaRecomendada,
        LocalDate vigenteHasta, String indicaciones) {}
public record Candidato(Long idCita, Long idPaciente, String paciente, String telefonoContacto,
        boolean telefonoInvalido, AlcancePreferencia alcance, int minLlegadaMin, int duracionMin,
        int residuoMin, LocalDateTime inscritoEn, Modalidad modalidad, LocalDateTime citaActual,
        LocalDateTime inicioPropuesto, int puntaje, boolean postergado, int posicion) {}

ReservaService:       Cita agendar(NuevaCitaComando c);
PreferenciaService:   void guardar(Long idCita, PreferenciaDatos d, Long idCuentaActor);
                      void revocar(Long idCita, Long idCuentaActor);
CitaEstadoService:    void confirmar(Long idCita, Long idLlamada, Long actor);
                      void cancelarPorPaciente(Long idCita, Long actor, String motivo);
                      void cancelarPorClinica(Long idCita, Long actor, String motivo);
                      void marcarLlegada(Long idCita, Long actor);
                      void iniciarAtencion(Long idCita, Long actor);
                      void finalizarAtencion(Long idCita, Long actor);
                      void marcarNoAsistio(Long idCita, Long actor);
                      void marcarRetiro(Long idCita, Long actor, String motivo);
                      void ajustarDuracion(Long idCita, int nuevaDuracionMin, Long actor);
                      void guardarIndicaciones(Long idCita, String texto, Long actor);      // médico
                      void guardarIndicacionesAdmin(Long idCita, String texto, Long actor); // recepción
                      VentanaPlazo ventana(Cita cita, Transicion t);
ReprogramacionService: void reprogramar(Long idCita, LocalDateTime nuevoInicio, Long actor);
                       void adelantarPorCupo(Long idCita, Long idOportunidad, LocalDateTime nuevoInicio, Long actor);
OportunidadService:   void liberar(Cita cita, LocalDateTime inicio, LocalDateTime fin);
                      void registrarCobertura(Cita nueva);
                      List<Oportunidad> abiertas();
CandidatoService:     List<Candidato> candidatos(Long idOportunidad);
                      void postergar(Long idOportunidad, Long idCita, Long actor);
CitaIndicadaService:  CitaIndicada indicar(Long idCitaOrigen, IndicacionComando c, Long actor);
                      void descartar(Long idIndicacion, String motivo, Long actor);
                      List<CitaIndicada> pendientes();                      // todas, por vigencia
                      List<CitaIndicada> pendientesDePaciente(Long idPaciente);
                      List<CitaIndicada> pendientesDeMedico(Long idMedico);
                      Optional<CitaIndicada> pendienteDePacienteParaMedico(Long idPaciente, Long idMedico);
                          // indicación PENDIENTE y vigente del paciente cuyo tipo atiende ese médico (aunque no la haya indicado él)
                      List<Medico> medicosQueAtienden(Short idTipoConsulta);  // activos con duración para ese tipo; primero el que indicó, si se pasa la indicación
                      int vencerVencidas();
ContactoService:      String telefonoDe(Cita cita);   // titular → teléfono de su paciente propio; sin titular → paciente
                      boolean telefonoInvalido(String telefono);
```

### 7.6 Pruebas (A1, en `src/test/java/pe/consultorio/citas/`)

```java
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {
    @Bean @ServiceConnection
    PostgreSQLContainer<?> postgres() { return new PostgreSQLContainer<>("postgres:16-alpine"); }
}
// DatosPrueba: @Component de prueba con métodos para crear datos en una línea
Medico crearMedico(String nombre, Especialidad e);            // con duraciones = duración por defecto de cada tipo
void crearHorario(Medico m, LocalDate desde, int diaSemana, LocalTime inicio, LocalTime fin);
Paciente crearPaciente(String nroDocumento);
Cuenta crearCuentaPaciente(String correo);                    // con su paciente propio
Cita crearCita(Medico m, Paciente p, LocalDateTime inicio, int minutos, EstadoCita estado);
```

---

## 8. Orden de integración

```
Fase 1 · cimientos        A1 → D2 → C1 → D1 → A2        (en paralelo: A3, que es Java puro)
Fase 2 · agenda y reserva A4 A5 A6 A7 A11 · C2 C3 C4 C5 · D3 D4 · B5 B9
Fase 3 · recuperación     A8 A9 A10 · B1 B2 B3 B4 B6 B7 B8 · C6 C7 C8 C9 · D5 D6 D7
```

- **A1 se fusiona primero** (entidades + cáscaras): sin ella nada compila. Debe ser lo primero que haga A.
- Mientras A1 no esté, B, C y D pueden ir armando sus plantillas desde el prototipo.
- Una tarea de B o C que llama a un servicio de A **puede empezar con la cáscara** y probarse de verdad cuando la tarea de
  A correspondiente se fusione. Cada tarea lo indica en «Depende de».

---

## 9. Paquete A — 40 %

**Responsable del núcleo.** Todo lo que garantiza que las citas sean correctas. 50 puntos.

### A1 · Entidades, enums, repositorios y cáscaras (8)

**Depende de:** nada; es la primera tarea de todas.

**Qué se espera:** el mapeo Java de las 25 tablas, para que la aplicación arranque contra PostgreSQL con
`ddl-auto=validate` sin errores. Además, las cáscaras de los servicios de A (§7) y la infraestructura de pruebas (§7.6).

**Entradas:**
- Tablas, columnas, tipos y nulabilidad: `src/main/resources/db/migration/V1__modelo_inicial.sql`.
- Tipos Java: tabla §6.3. Valores de los enums: §7.1 (son los mismos que los CHECK de V1).
- Patrón de entidad:

```java
@Entity
@Table(name = "cita")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Long idCita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)              // admite NULL
    @JoinColumn(name = "id_cuenta_titular")
    private Cuenta cuentaTitular;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoCita estado;

    @Column(name = "es_primera_vez", nullable = false)
    private boolean esPrimeraVez;
    // … resto de columnas, getters y setters
}
```

- Relaciones: toda FK es un `@ManyToOne(fetch = LAZY)`. Único `@OneToMany`: `HorarioVersion.franjas`
  (`cascade = ALL, orphanRemoval = true`). En `cita`, `id_medico` e `id_especialidad` son dos `@ManyToOne`
  separados (`Medico` y `Especialidad`).
- Claves compuestas (`cuenta_paciente`, `medico_especialidad`, `medico_tipo_consulta`, `candidato_postergado`):
  `@EmbeddedId` con una clase `…Id` `@Embeddable` que implementa `Serializable`, `equals` y `hashCode`.
- Clave de texto: `PlazoTransicion` usa `@Id @Enumerated(STRING) Transicion transicion`; `ParametroConsultorio` usa
  `@Id Short idParametro` (siempre 1, sin `@GeneratedValue`); `TokenRecuperacion` usa `@Id String jti`.

**Salida:**
- `entity/`: 25 clases + 15 enums + 4 clases `…Id`.
- `repository/`: un repositorio por entidad (`CitaRepository extends JpaRepository<Cita, Long>`, etc.).
- `service/` y `service/disponibilidad/`: cáscaras de todas las clases de §7.5 con `@Service`.
- `dto/`: los `record` de §7.5.
- Pruebas: `PostgresTestConfig`, `DatosPrueba`, `EsquemaTest`.

**Hecho cuando:**
- `EsquemaTest` (`@SpringBootTest` + Testcontainers) arranca el contexto (la validación pasa) y comprueba con
  `JdbcTemplate` que `select count(*) from pg_constraint where conname = 'excl_cita_solape'` devuelve 1.
- `./mvnw spring-boot:run` arranca contra el Docker local sin `Schema-validation` en el log.

### A2 · Errores, auditoría, reloj y Logback (3)

**Depende de:** A1.

**Qué se espera:** la infraestructura de errores y registro que usan todos.

**Entradas y salida:**
- `config/RelojConfig` (§7.2) y `@EnableScheduling` en una clase de `config/`.
- `exception/`: las clases de §7.2. `ErroresBaseDatos.esSolape(e)` recorre las causas hasta un `SQLException` con
  `getSQLState().equals("23P01")`.
- `exception/ManejadorErrores` (`@ControllerAdvice`):
  - `RecursoNoEncontradoException` → vista `error/404` con estado 404.
  - `ReglaNegocioException` no atrapada → vista `error/negocio` con el mensaje.
  - Cualquier otra → `log.error("Error no controlado [{}]", idError, e)` y vista `error/500` con «Ocurrió un error
    inesperado. Código: {idError}», donde `idError` son los primeros 8 caracteres de un UUID.
- `templates/error/{404,negocio,500}.html` con el fragmento base de D1.
- `service/AuditoriaService.registrar(...)`: inserta en `auditoria` con `fecha_hora = ahora`. Usa
  `@Transactional(propagation = REQUIRES_NEW)` para que el registro quede **aunque la operación que lo llama falle**
  (por ejemplo un cambio de horario rechazado).
- `src/main/resources/logback-spring.xml`: consola + archivo `logs/citas.log` con rotación diaria y 14 días de
  historial; patrón `%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{peticion}] %logger{36} - %msg%n`.
- `config/FiltroCorrelacion` (`OncePerRequestFilter`): pone en el MDC `peticion` = 8 caracteres de un UUID, registra
  `método ruta → estado (ms)` en nivel INFO y limpia el MDC al final.

**Hecho cuando:** prueba de `ErroresBaseDatos.esSolape` con un `SQLException("x", "23P01")` envuelto en dos causas;
una petición a una ruta inexistente muestra `error/404`; `logs/citas.log` registra cada petición con su código.

### A3 · Motor de disponibilidad con Guava (6)

**Depende de:** nada (Java puro). Se puede hacer desde el primer día.

**Qué se espera:** el cálculo de horarios libres y su orden. Es el código más importante del proyecto y el que
justifica la librería Guava.

**Entradas:** rangos de tiempo, `Range.closedOpen(inicio, fin)` de `LocalDateTime` (incluye el inicio, excluye el fin:
una cita que termina a las 10:00 no choca con otra que empieza a las 10:00).

**Salida:** `service/disponibilidad/MotorDisponibilidad.java` (clase `final` con métodos estáticos, sin Spring) y
`Rejilla.java`:

```java
public static RangeSet<LocalDateTime> bloquesLibres(Collection<Range<LocalDateTime>> franjas,
                                                    Collection<Range<LocalDateTime>> ocupados)
public static List<Oferta> ofertas(RangeSet<LocalDateTime> libres, int duracionMin,
                                   int incrementoMin, int huecoMinimoUtilMin)
```

**Algoritmo:**
1. `bloquesLibres`: `TreeRangeSet.create()`, `addAll(franjas)`, `removeAll(ocupados)`. Guava une sola los rangos
   contiguos (11:30–12:00 + 12:00–14:00 = 11:30–14:00).
2. `ofertas`, para cada bloque de `libres.asRanges()`:
   - `L` = minutos del bloque, `D` = `duracionMin`. Si `L < D`, el bloque no ofrece nada.
   - Residuo `R = L − D`. Calce: `R == 0` → `PERFECTO`; `R >= huecoMinimoUtil` → `VENDIBLE`; si no → `BASURA`.
   - Inicios: desde el inicio del bloque, sumando `incrementoMin`, mientras `inicio + D <= fin del bloque`.
3. Orden final: `calce` (PERFECTO, VENDIBLE, BASURA), luego `residuoMin` ascendente, luego `inicio` ascendente.
4. `protegida` es siempre `false` en el MVP (ver A12).

`Rejilla.redondearArriba(10:07, 15)` = 10:15; `Rejilla.redondearArriba(10:15, 15)` = 10:15.

**Hecho cuando:** `MotorDisponibilidadTest` (JUnit puro, fecha 2026-11-11) pasa estos casos:

| # | Franjas | Ocupados | D / inc / hueco | Resultado esperado |
| --- | --- | --- | --- | --- |
| 1 | 08:00–16:00 | 08:00–11:30, 14:00–16:00 | — | un solo bloque 11:30–14:00 |
| 2 | 08:00–12:00 y 12:00–16:00 | — | — | un solo bloque 08:00–16:00 |
| 3 | 08:00–13:00 | 10:00–11:00 | — | dos bloques: 08:00–10:00 y 11:00–13:00 |
| 4 | 09:00–10:00 | — | 30 / 15 / 30 | inicios 09:00, 09:15, 09:30 (09:45 no: terminaría 10:15) |
| 5 | 09:00–09:30, 10:00–11:00, 12:00–12:45 | — | 30 / 15 / 30 | orden: 09:00 (PERFECTO) · 10:00, 10:15, 10:30 (VENDIBLE, R=30) · 12:00, 12:15 (BASURA, R=15) |
| 6 | 09:00–09:20 | — | 30 / 15 / 30 | lista vacía |

### A4 · Servicio de disponibilidad y bloqueo temporal (4)

**Depende de:** A1, A3, D2.

**Qué se espera:** alimentar el motor con los datos de un médico y un día, y retener un horario 5 minutos mientras el
usuario completa el formulario.

**Entradas, para `idMedico` y `fecha`:**
- **Franjas:** la `horario_version` vigente ese día (`vigente_desde <= fecha` y `vigente_hasta` nulo o `>= fecha`) y sus
  `franja_horaria` con `dia_semana == fecha.getDayOfWeek().getValue()` (1 = lunes … 7 = domingo) → rango
  `fecha.atTime(hora_inicio)` a `fecha.atTime(hora_fin)`.
- **Ocupados:**
  - `excepcion_horario` del médico que cubren la fecha (`fecha_inicio <= fecha <= fecha_fin`): con horas nulas, todo el
    día; si no, `fecha.atTime(hora_inicio)`–`fecha.atTime(hora_fin)`.
  - `cita` del médico ese día cuyo estado **no** es `CANCELADA_POR_PACIENTE` ni `CANCELADA_POR_CLINICA`.
  - `bloqueo_temporal` del médico con `expira_en > ahora` y `id_cuenta` **distinta** de `idCuentaQuePide`.
  - Si `fecha` es hoy: `Range.lessThan(Rejilla.redondearArriba(ahora, incremento))` (no se ofrece el pasado).
- Incremento y hueco mínimo: `ConfiguracionService.incremento()` y `huecoMinimoUtil()`.
- Médico con `activo = false` → lista vacía.

**Salida:** `DisponibilidadService` y `BloqueoTemporalService` (§7.5).
- `ofertasProximas`: recorre desde `desde` hasta `desde + 59 días` y devuelve los **3 primeros días** que tengan al
  menos una oferta. Entre días, orden cronológico.
- `bloquesLibresDelDia`: igual, pero **sin** bloqueos temporales ni el recorte de «hoy» (lo usa A8).
- `BloqueoTemporalService.tomar`: borra los bloqueos vencidos y el bloqueo anterior de esa cuenta; si hay otro bloqueo
  vigente de otra cuenta que se cruza con `[inicio, fin)` del mismo médico → `HorarioOcupadoException`; si no, inserta
  con `expira_en = ahora + parametros().bloqueoTemporalMin`.

**Hecho cuando:** `DisponibilidadServiceTest` (Testcontainers): médico con franja lunes 08:00–12:00 y una cita
09:00–09:30 → las ofertas de 30 min del lunes no incluyen 09:00 ni 08:45 si hay un bloqueo de otra cuenta en
08:45–09:00; una excepción de día completo deja el día sin ofertas; `ofertasProximas` salta los días sin horario.

### A5 · Reserva y preferencias (6)

**Depende de:** A4.

**Qué se espera:** crear una cita de forma correcta frente a la concurrencia, desde cualquier canal.

**Entradas:** `NuevaCitaComando` (§7.5). Lo arman C6 (portal, canal `WEB`), B6 (recepción, canal elegido) y B9
(médico, canal `CONSULTA_MEDICA`).

**`ReservaService.agendar` (`@Transactional`), en este orden:**
1. Cargar paciente, médico (`activo`), tipo (`activo`) y especialidad. El médico debe ejercer esa especialidad
   (`medico_especialidad`); si no → `ReglaNegocioException("El médico no atiende esa especialidad.")`.
2. Validar:
   - `inicio` y `duracionMin` en la rejilla (`Rejilla.enRejilla`, `duracionMin % incremento == 0`) e `inicio > ahora`.
   - Modalidad permitida por el tipo (`AMBAS` o igual).
   - Canal `WEB`: el tipo es `reservable_web`, **o** viene `idIndicacion` de ese mismo tipo.
   - Con `idCuentaTitular`: esa cuenta es la del paciente propio, o tiene un `cuenta_paciente` con ese paciente y
     `revocado_en` nulo.
   - Canal `CONSULTA_MEDICA`: `autorizacionVerbal == true`.
3. `es_primera_vez` = el paciente **no** tiene ninguna cita `REALIZADA`.
4. Crear `Cita` con estado `PROGRAMADA`, `fin = inicio + duracionMin`, `creado_en = ahora`, y guardar con
   **`saveAndFlush`** dentro de `try/catch (DataIntegrityViolationException e)`: si `ErroresBaseDatos.esSolape(e)` →
   `throw new HorarioOcupadoException()`.
5. `cita_evento`: `CREACION`, `estado_nuevo = PROGRAMADA`, `inicio_nuevo`/`fin_nuevo`, `id_cuenta = idCuentaActor`.
6. Si `preferencia != null` → `PreferenciaService.guardar`.
7. Si `idIndicacion != null`: la indicación está `PENDIENTE` y vigente, y **el paciente de la cita es el paciente de la
   indicación** (el de su cita de origen); si es otro → `ReglaNegocioException("Esta indicación es para otro paciente.")`.
   El médico puede ser distinto del que la indicó. Si el tipo es el de la indicación → `AGENDADA` con
   `id_cita_generada`; si el tipo es otro, la indicación sigue `PENDIENTE`.
8. Canal `CONSULTA_MEDICA` → auditoría `AUTORIZACION_VERBAL` (entidad `CITA`, id de la cita).
9. `OportunidadService.registrarCobertura(cita)` y `BloqueoTemporalService.liberar(idCuentaActor)`.

**`PreferenciaService`:** `guardar` crea la preferencia (`inscrito_en = consentido_en = ahora`) o, si existe, actualiza
alcance, llegada y días, pone `consentido_en = ahora`, `revocado_en = null` y **no toca `inscrito_en`**. `revocar` pone
`revocado_en = ahora`. Las dos registran auditoría `PREFERENCIA_CAMBIO` con el detalle anterior → nuevo.

**Hecho cuando** (`ReservaServiceTest`, Testcontainers):
- Se agenda una cita y existe su evento `CREACION`.
- Una segunda cita que se cruza con la primera lanza `HorarioOcupadoException`.
- **Concurrencia:** dos hilos agendan el mismo horario a la vez (`CountDownLatch`) → exactamente una cita guardada y el
  otro hilo recibe `HorarioOcupadoException`.
- Un `INSERT` directo con `JdbcTemplate` de una cita a las 14:13 es rechazado por la base (`chk_cita_rejilla`).
- Un `INSERT` directo de un `cita_evento` con `estado_nuevo = 'CONFIRMADA'` y sin `id_llamada` es rechazado.

### A6 · Estados y plazos (5)

**Depende de:** A5.

**Qué se espera:** todas las transiciones de §13.2, con su plazo, su permiso y su evento, en el mismo
`@Transactional`.

**Entradas:**
- Estado actual de la cita, `ahora` (del `Clock`) y la fila de `plazo_transicion` de la transición
  (`ConfiguracionService.plazo(t)`).
- Rol del actor: se carga su `Cuenta`. Un médico solo actúa sobre citas cuyo `medico.cuenta` es la suya; si no →
  `RecursoNoEncontradoException`.

**Cálculo de la ventana (`ventana(cita, t)`):**
- `desde` = `minutos_desde` nulo ? sin límite : `cita.inicio + minutos_desde`.
- `hasta` = `hasta_fin_dia == 0` → inicio del día siguiente al de la cita · `hasta_fin_dia == 1` → inicio de dos días
  después · si no, `minutos_hasta` nulo ? sin límite : `cita.inicio + minutos_hasta`.
- Permitido si `ahora >= desde` y `ahora < hasta`. Si no → `PlazoVencidoException` con la hora concreta, por ejemplo
  «Ya pasó el plazo para cancelar esta cita (hasta las 08:30 del 12/11).» o «Aún no se puede marcar la inasistencia: se
  habilita a las 08:35.».

**Salida:** `CitaEstadoService` (§7.5). Reglas por método en §13.2. Además:
- `confirmar` exige que la cita ya esté en la cola: `ahora >= inicio − anticipacion_confirm_h`.
- `marcarLlegada` pone `hora_llegada = ahora`. `iniciarAtencion` exige `ASISTIO` (si no: «Recepción aún no registró la
  llegada del paciente.») y pone `inicio_real`. `finalizarAtencion` exige `inicio_real`, pone `fin_real` y pasa a
  `REALIZADA`.
- Cancelar (por paciente o por clínica) llama después a `OportunidadService.liberar(cita, inicio, fin)`.
- Cancelación registrada por personal (recepción, admin o médico): motivo obligatorio.
- `ajustarDuracion`: estados `PROGRAMADA` o `CONFIRMADA`; el médico necesita `tipo.ajuste_medico` y la recepción
  `tipo.ajuste_recepcion`; el administrador siempre puede. `fin = inicio + nueva duración` con `saveAndFlush`; si es
  `23P01` → `HorarioOcupadoException("La nueva duración se cruza con la siguiente cita.")`. Si se acorta, libera el
  sobrante con `OportunidadService.liberar`. Evento `AJUSTE_DURACION` con los horarios anterior y nuevo.

**Hecho cuando** (`CitaEstadoServiceTest`, con `Clock` fijo): cancelar después del inicio → `PlazoVencidoException`;
no asistió a inicio+4 min → error, a inicio+5 → permitido; retiro sin motivo → error; finalizar sin iniciar → error;
cada transición deja exactamente un evento con estado anterior y nuevo.

### A7 · Reprogramación (2)

**Depende de:** A6.

**Qué se espera:** mover una cita a otro horario conservando su id.

**`reprogramar(idCita, nuevoInicio, actor)`:** estado `PROGRAMADA` o `CONFIRMADA`; la cita aún no empezó
(`inicio > ahora`); misma duración, mismo médico y misma modalidad; `nuevoInicio` en la rejilla y en el futuro.
**Sin plazo de antelación.** Pueden hacerlo el paciente desde Mis citas (C9), recepción, el administrador y el médico
sobre las suyas; que el paciente vea esa cita lo comprueba el controlador, igual que al cancelar. Actualiza `inicio`, `fin` y `estado = PROGRAMADA` (pierde la confirmación) con `saveAndFlush`
(`23P01` → `HorarioOcupadoException`). Evento `REPROGRAMACION` con estado y horarios anteriores y nuevos. Luego
`OportunidadService.liberar(cita, inicioAnterior, finAnterior)` y `registrarCobertura(cita)`.

**`adelantarPorCupo(idCita, idOportunidad, nuevoInicio, actor)`:** `reprogramar` + la preferencia de esa cita queda
consumida (`revocado_en = ahora`, auditoría `PREFERENCIA_CAMBIO` con motivo «Consumida al aceptar un cupo»).

**Hecho cuando:** tras reprogramar, el `id_cita` es el mismo, el estado es `PROGRAMADA` aunque estuviera `CONFIRMADA`, y
existe un evento `REPROGRAMACION`.

### A8 · Oportunidades (horarios liberados) y tareas programadas (4)

**Depende de:** A6.

**Qué se espera:** registrar cada horario liberado como bloque completo, y cerrarlo cuando alguien lo ocupa o cuando
pasa su hora.

**`liberar(cita, inicio, fin)`** (se llama dentro de la transacción que canceló, reprogramó o acortó):
1. Si `fin <= ahora`, no hace nada.
2. `libres = disponibilidadService.bloquesLibresDelDia(medico, fecha)`; `bloque = libres.rangeContaining(max(inicio,
   redondearArriba(ahora)))`. Se recorta para que no empiece antes de `redondearArriba(ahora)`.
3. Si el bloque dura menos que `huecoMinimoUtil()`, no hace nada.
4. Las oportunidades `ABIERTA` del médico que se cruzan con el bloque pasan a `REEMPLAZADA` (`cerrada_en = ahora`).
5. Inserta la oportunidad `ABIERTA` con el bloque, `id_cita_origen = cita`, `creada_en = ahora` e
   `id_oportunidad_padre` = la más temprana de las reemplazadas (o nulo).

**`registrarCobertura(nueva)`:** cada oportunidad `ABIERTA` del médico que se cruza con `[nueva.inicio, nueva.fin)` pasa
a `CUBIERTA` (`cerrada_en`, `id_cita_cobertura = nueva`). Los sobrantes `[op.inicio, nueva.inicio)` y
`[nueva.fin, op.fin)` que duren al menos `huecoMinimoUtil()` se insertan como nuevas `ABIERTA` con
`id_oportunidad_padre = op` y el mismo `id_cita_origen`.

**`abiertas()`:** estado `ABIERTA`, ordenadas por `inicio` ascendente (las más urgentes primero).

**`TareasProgramadas`** (`@Scheduled(fixedDelay = 300000)`, cada 5 minutos): oportunidades `ABIERTA` con
`inicio <= ahora` → `EXPIRADA`; borrar bloqueos temporales vencidos; `CitaIndicadaService.vencerVencidas()`.

**Hecho cuando** (Testcontainers): franja 08:00–14:00 y citas 08:00–11:30, 12:00–12:30 y 12:30–14:00. Cancelar
12:00–12:30 → oportunidad 11:30–12:30. Cancelar 12:30–14:00 → la anterior queda `REEMPLAZADA` y aparece 11:30–14:00 con
padre. Agendar 11:30–12:00 → `CUBIERTA` con su cita y nueva `ABIERTA` 12:00–14:00.

### A9 · Candidatos y puntaje (4)

**Depende de:** A8.

**Qué se espera:** para un bloque liberado, la lista ordenada de pacientes a quienes llamar.

**Entradas:** la oportunidad (`inicio`, `fin`, médico), las preferencias vigentes y sus citas.

**Un paciente es candidato si cumple todo esto** (D = duración de su cita, `fin − inicio`):
1. Su preferencia tiene `revocado_en` nulo.
2. Su cita sigue vigente (`PROGRAMADA` o `CONFIRMADA`) y empieza **después** del fin del bloque.
3. Alcance `MEDICO`: su cita es con el médico del bloque. Alcance `ESPECIALIDAD`: el médico del bloque ejerce la
   especialidad de su cita.
4. Acepta ese día: `fecha del bloque >= fecha de su cita − dias_previos`.
5. **Llega a tiempo:** `inicioPropuesto = max(op.inicio, redondearArriba(ahora + min_llegada_min))` y
   `inicioPropuesto + D <= op.fin`.
6. No tiene otra cita no cancelada que se cruce con `[inicioPropuesto, inicioPropuesto + D)`.
7. No aceptó ya otro cupo hoy (no hay `registro_llamada` `CUPO_LIBERADO` / `ACEPTO` de hoy para una cita suya).

**Puntaje** (constantes con nombre en `CandidatoService`; `calcularPuntaje` es `static` para probarlo sin Spring):

| Constante | Valor | Se suma cuando |
| --- | --- | --- |
| `PESO_MISMO_MEDICO` | 30 | su cita es con el médico del bloque |
| `PESO_MISMA_ESPECIALIDAD` | 10 | alcance `ESPECIALIDAD` y otro médico |
| `PESO_TELECONSULTA` | 15 | su cita es `TELECONSULTA` |
| `PESO_CALCE_MAX` | 20 | `20 × D / L` (L = minutos del bloque), entero |
| `PESO_ANTIGUEDAD_MAX` | 15 | 1 punto por día desde `inscrito_en`, máximo 15 |
| `PESO_LLEGADA_MAX` | 10 | `10 × (120 − min(min_llegada_min, 120)) / 120`, entero |
| `PESO_DISPONIBLE_HOY` | 5 | `consentido_en` es del mismo día que el bloque |

**Orden:** primero los **no** postergados, por puntaje descendente y, a igual puntaje, por `inscrito_en` ascendente;
después los postergados (`candidato_postergado`), por `postergado_en` ascendente. `posicion` = 1, 2, 3…

**`postergar`:** inserta `candidato_postergado` o, si ya existe, actualiza `postergado_en = ahora`. No es una llamada.

**`ContactoService`:** `telefonoDe(cita)` = teléfono del paciente propio de `cita.cuentaTitular`; sin titular, el del
paciente. `telefonoInvalido(t)` = existe un `registro_llamada` con resultado `NUMERO_INVALIDO` y
`telefono_llamado = t`.

**Hecho cuando:** `CandidatoServiceTest` con un paciente que cumple todo y uno por cada condición que falla (solo el
primero aparece); prueba unitaria de `calcularPuntaje` con un caso calculado a mano; un postergado queda último aunque
tenga más puntaje.

### A10 · Citas indicadas (2)

**Depende de:** A5.

**Qué se espera:** el médico prescribe una siguiente cita; queda pendiente hasta que se agende, venza o se descarte.

**`indicar(idCitaOrigen, c, actor)`:** la cita de origen es del médico actor; el tipo está activo;
`duracionMin % incremento == 0`; `fechaRecomendada >= hoy`; `vigenteHasta >= fechaRecomendada`. Estado `PENDIENTE`,
`creado_en = ahora`. El valor por defecto de `vigenteHasta` que muestran las pantallas es
`hoy + parametros().vigenciaIndicadaDias`.

**`descartar`:** solo `PENDIENTE`; motivo obligatorio → `DESCARTADA` con `motivo_descarte`.

**Consultas:** `pendientes()` = `PENDIENTE`, ordenadas por `vigente_hasta` ascendente. `pendientesDePaciente` y
`pendientesDeMedico` (el médico de la cita de origen), solo vigentes (`vigente_hasta >= hoy`).
`pendienteDePacienteParaMedico(idPaciente, idMedico)`: la indicación pendiente y vigente del paciente cuyo tipo tiene
`medico_tipo_consulta` con ese médico, **sea o no quien la indicó**; si hay varias, la de vigencia más corta.
**La indicación es del paciente**, no del médico: el paciente de la indicación es el de su cita de origen.
`vencerVencidas()`: `PENDIENTE` con `vigente_hasta < hoy` → `VENCIDA`; devuelve cuántas cambió.

**Hecho cuando:** indicar, agendar con el mismo tipo (A5) → `AGENDADA` con `id_cita_generada`; agendar otro tipo → sigue
`PENDIENTE`; con el `Clock` un día después de la vigencia, `vencerVencidas()` la pasa a `VENCIDA`.

### A11 · Mi horario (servicio y pantalla del médico) (6)

**Depende de:** A1, A2, D1.

**Qué se espera:** el médico define cuándo atiende. **Un cambio que deja fuera citas ya agendadas se rechaza**, y se le
muestran las citas que lo impiden.

**Entradas:** prototipo `prototipo/medico-horario.html` (tres secciones: versiones, excepciones, duraciones por tipo).

**Salida:** `HorarioService`, `HorarioConCitasException extends ReglaNegocioException` (con `List<Cita> citas`),
`controller/medico/HorarioController`, `templates/medico/horario.html`.

| Ruta | Hace |
| --- | --- |
| `GET /medico/horario` | Versiones (vigente y futuras), excepciones futuras y duraciones del médico de la sesión |
| `POST /medico/horario/versiones` | Nueva versión desde una fecha, con sus franjas |
| `POST /medico/horario/excepciones` | Nueva excepción |
| `POST /medico/horario/excepciones/{id}/eliminar` | Quita una excepción futura |
| `POST /medico/horario/duraciones` | Cambia la duración de un tipo (`medico_tipo_consulta.duracion_configurada`) |

**Reglas:**
- **Nueva versión:** `desde > hoy`; franjas en la rejilla, `fin > inicio`, sin cruces en el mismo día. Si ya existe una
  versión que empieza en `desde` o después → error «Ya hay una versión programada desde el dd/MM. Elimínala o cámbiala.».
  **Citas afectadas** = citas no canceladas del médico con `inicio >= desde` que **no** caben completas en alguna franja
  de la nueva versión para su día de la semana. Si hay alguna → auditoría `CAMBIO_HORARIO_RECHAZADO` y
  `HorarioConCitasException("Este cambio deja fuera {n} citas agendadas. Reprográmalas primero.")`. Si no, la versión
  vigente se cierra con `vigente_hasta = desde − 1 día` y se inserta la nueva con `vigente_hasta` nulo.
- **Excepción:** `fecha_fin >= fecha_inicio`; horas ambas nulas (día completo) o ambas en la rejilla con `fin > inicio`;
  motivo obligatorio. Se rechaza igual si hay citas no canceladas dentro del rango.
- **Duración:** múltiplo del incremento y mayor que 0; auditoría `CONFIG_CAMBIO`.
- En pantalla, el rechazo lista las **5 citas más próximas** (fecha, hora, paciente) y «y {n − 5} más» con enlace a
  `/medico/agenda?fecha=<fecha de la primera>`.
- Los `<select>` de hora usan `Rejilla.horasDelDia(06:00, 22:00, incremento)`.

**Hecho cuando:** `HorarioServiceTest`: con una versión martes 08:00–18:00 y una cita un martes futuro a las 17:00, una
nueva versión martes 08:00–16:00 se rechaza listando esa cita y deja un registro de auditoría; sin esa cita, se acepta y
la versión anterior queda cerrada el día previo.

### A12 · (Opcional, fuera del MVP) Política de protección de bloques (+3)

Solo si A terminó todo lo anterior. Con `politica_proteccion = BLANDA`, en cada día se marca `protegida = true` a las
ofertas del **último bloque del día** cuya longitud supera `minutos_protegidos`, siempre que (a) haya al menos otra
oferta viable ese día y (b) falten más de `ventana_liberacion_h` horas para su inicio. Con `DURA`, se marca siempre que
el bloque supere el umbral. Las protegidas van al final del día; para el **paciente** se ocultan, para recepción y
médicos se muestran con una marca.

---

## 10. Paquete B — 25 %

**Pantallas de recepción y agenda del médico.** 31 puntos. Todo lo que escribe en citas lo hace llamando a los servicios
de A, nunca con repositorios.

### B1 · Componente de cola y registro de llamadas (3)

**Depende de:** A6, A9, A10.

**Qué se espera:** las tres colas de recepción (confirmaciones, cupos liberados, citas pendientes) **son el mismo
componente visual con otra fuente de datos**; no se programa tres veces. Cada llamada queda registrada.

**Salida:**
- `dto/FilaCola`: `record FilaCola(Long id, String paciente, String telefono, boolean telefonoInvalido, String medico,
  LocalDateTime fechaHora, String detalle, int intentos, String estado, boolean urgente)`.
- `service/ColaService`:
  - `confirmaciones(LocalDate fecha, Long idMedico, Short idEspecialidad)`: citas `PROGRAMADA` con
    `ahora < inicio <= ahora + anticipacion_confirm_h` (filtros opcionales), por `inicio` ascendente. Una cita cancelada
    desde el portal **desaparece sola** porque ya no está `PROGRAMADA`.
  - `cupos()`: `OportunidadService.abiertas()`, con el número de candidatos en `detalle`.
  - `pendientes()`: `CitaIndicadaService.pendientes()`; `urgente = true` si vence en 7 días o menos.
  - `intentos` = llamadas registradas para ese elemento en esa cola.
- `service/LlamadaService` (`@Transactional`). Siempre inserta `registro_llamada` con `id_cuenta_operador = actor`,
  `telefono_llamado = ContactoService.telefonoDe(cita)` y `fecha_hora = ahora`:
  - `registrarConfirmacion(idCita, resultado, observacion, actor)`: `CONFIRMO` → `CitaEstadoService.confirmar(idCita,
    idLlamada, actor)`; `CANCELO` → `cancelarPorPaciente` (observación obligatoria como motivo); `REPROGRAMO` → solo
    registra (el controlador abre la reprogramación); `NO_CONTESTO` y `NUMERO_INVALIDO` → solo registran.
  - `registrarCupo(idOportunidad, idCita, posicion, resultado, observacion, inicioPropuesto, actor)`: `ACEPTO` →
    `ReprogramacionService.adelantarPorCupo`; el resto solo registra.
  - `registrarPendiente(idIndicacion, resultado, observacion, actor)`: el teléfono sale de la cita de origen;
    `NO_LE_INTERESA` → `CitaIndicadaService.descartar(id, "No le interesa", actor)`; `AGENDO` → solo registra (el
    controlador abre la nueva cita con la indicación).
  - Resultados permitidos por cola: §13.4. Otro resultado → `ReglaNegocioException`.
- `templates/fragmentos/cola.html` :: `tabla(filas, accion)`: la tabla del prototipo `recepcion-confirmaciones.html`
  con un botón [Registrar resultado] por fila que abre un modal con los resultados de esa cola (lista que manda el
  controlador) y un campo de observación. Teléfono inválido: badge rojo «Número inválido». Fila urgente: `table-warning`.

**Hecho cuando:** `LlamadaServiceTest`: `CONFIRMO` deja la cita `CONFIRMADA` y un evento con `id_llamada`; `CANCELO`
la cancela y crea su oportunidad; un resultado de otra cola es rechazado.

### B2 · Confirmaciones (2)

**Depende de:** B1. **Prototipo:** `prototipo/recepcion-confirmaciones.html`.

| Ruta | Hace |
| --- | --- |
| `GET /recepcion/confirmaciones?fecha=&medico=&especialidad=` | Cola + contador de pendientes |
| `POST /recepcion/confirmaciones/{idCita}/resultado` | `resultado`, `observacion` → `LlamadaService.registrarConfirmacion` |

Con `REPROGRAMO` redirige a `/recepcion/citas/{idCita}/reprogramar`. Columnas: paciente, teléfono, médico, fecha y
hora, intentos, estado.

**Hecho cuando:** confirmar desde la cola cambia el estado y la fila desaparece; la cita no se puede confirmar desde
ninguna otra pantalla.

### B3 · Cupos liberados (4)

**Depende de:** B1. **Prototipo:** `prototipo/recepcion-cupos.html`.

| Ruta | Hace |
| --- | --- |
| `GET /recepcion/cupos` | Bloques abiertos: médico, fecha, hora, duración del bloque, tiempo restante hasta el inicio, nº de candidatos |
| `GET /recepcion/cupos/{idOportunidad}` | Candidatos de `CandidatoService.candidatos`: nombre, teléfono, médico o especialidad, llegada declarada, duración, calce exacto o residuo, antigüedad, modalidad, posición |
| `POST /recepcion/cupos/{idOportunidad}/candidatos/{idCita}/resultado` | `resultado`, `observacion`, `posicion`, `inicioPropuesto` → `LlamadaService.registrarCupo` |
| `POST /recepcion/cupos/{idOportunidad}/candidatos/{idCita}/postergar` | `CandidatoService.postergar` |

Tras `ACEPTO`: mensaje «Cita adelantada al dd/MM HH:mm.» y vuelta a la lista (si sobró parte del bloque, aparece como
cupo nuevo).

**Hecho cuando:** con datos de prueba, un candidato acepta, su cita conserva el id y cambia de hora, y el bloque
desaparece de la lista o queda su remanente.

### B4 · Citas pendientes por agendar (2)

**Depende de:** B1. **Prototipo:** `prototipo/recepcion-pendientes.html`.

| Ruta | Hace |
| --- | --- |
| `GET /recepcion/pendientes` | Indicaciones pendientes: paciente, teléfono, médico que indicó, tipo, desde cuándo, días de vigencia restantes |
| `POST /recepcion/pendientes/{idIndicacion}/resultado` | → `LlamadaService.registrarPendiente`; `AGENDO` redirige a `/recepcion/cita/nueva?indicacion={id}` |

**Hecho cuando:** «No le interesa» la descarta y sale de la cola; «Agendó» abre la nueva cita con tipo y duración
prellenados.

### B5 · Agenda de recepción (5)

**Depende de:** A6, A7. **Prototipo:** `prototipo/recepcion-agenda.html`.

**Salida:** `service/AgendaService` (consultas de agenda de recepción y del médico), `controller/recepcion/AgendaRecepcionController`,
`templates/recepcion/agenda.html` y `templates/recepcion/reprogramar.html`.

| Ruta | Hace |
| --- | --- |
| `GET /recepcion/agenda?fecha=&medico=&especialidad=&estado=&modalidad=&vista=columnas\|lista&cita=` | Día por columnas (una por médico) o lista; con `cita=` abre el panel de detalle |
| `POST /recepcion/citas/{id}/llegada` | `marcarLlegada` |
| `POST /recepcion/citas/{id}/estado` | `nuevoEstado` ∈ {`CANCELADA_POR_PACIENTE`, `CANCELADA_POR_CLINICA`, `NO_ASISTIO`} + `motivo`. Si la cita está `ASISTIO` y se pide `NO_ASISTIO` → `marcarRetiro`. **`CONFIRMADA` no aparece aquí** |
| `GET /recepcion/citas/{id}/reprogramar?fecha=` | Horarios de `DisponibilidadService.ofertasDelDia` con la duración de la cita; se ven todos, los `PERFECTO` destacados |
| `POST /recepcion/citas/{id}/reprogramar` | `nuevoInicio` → `ReprogramacionService.reprogramar` |
| `POST /recepcion/citas/{id}/duracion` | `ajustarDuracion` (solo si el tipo lo permite a recepción; si no, el control no se muestra) |
| `POST /recepcion/citas/{id}/indicaciones-admin` | `guardarIndicacionesAdmin` |

Panel de detalle: paciente (nombre, documento, edad), teléfono de contacto (`ContactoService`), titular si la agendó otra
cuenta, tipo, duración, modalidad, canal, estado, llegada, indicaciones del médico y administrativas. Los huecos libres
del día (`bloquesLibresDelDia`) se dibujan en gris entre las citas.

**Hecho cuando:** se marca la llegada (estado `ASISTIO`), se cancela con motivo, se reprograma conservando el id; el
`HorarioOcupadoException` vuelve a la lista de horarios con el mensaje.

### B6 · Nueva cita desde recepción (4)

**Depende de:** A5, A10, B8 (búsqueda de pacientes). **Prototipo:** `prototipo/recepcion-nueva-cita.html`.

**Qué se espera:** el mismo agendamiento del portal, en una sola pantalla por pasos, con todos los tipos de consulta y
el canal de origen.

**Salida:** `dto/NuevaCitaRecepcionForm` (`Serializable`, en sesión con `@SessionAttributes`),
`controller/recepcion/NuevaCitaRecepcionController`, `templates/recepcion/nueva-cita.html`.

Pasos (`GET/POST /recepcion/cita/nueva?paso=1..8`; `?indicacion={id}` precarga paciente, tipo y duración):
1. **Paciente:** buscar por documento, nombre o teléfono (`PacienteService.buscar`) o [Registrar paciente nuevo]. Si es
   para un tercero, buscar la **cuenta titular** por correo y marcar la declaración de consentimiento (crea el
   `cuenta_paciente` si no existe). Sin cuenta titular, el contacto es el teléfono del paciente.
2. **Canal de origen** (obligatorio): `PRESENCIAL`, `TELEFONO`, `WHATSAPP`, `DERIVACION`.
3. **Cita indicada:** si el paciente tiene una pendiente, se ofrece por defecto; se puede elegir otro tipo.
4. **Tipo** (todos los activos) + **modalidad** (las que permite el tipo).
5. **Médico** (activos que ejercen la especialidad y tienen duración para el tipo).
6. **Horarios:** `ofertasProximas` con la duración de `medico_tipo_consulta` (o la de la indicación). Al elegir:
   `BloqueoTemporalService.tomar` y contador visible (`data-cuenta-regresiva` con los segundos restantes).
7. **Disponibilidad inmediata:** casilla + alcance, tiempo de llegada (valor + unidad min/h, se convierte a minutos;
   con teleconsulta se sugiere 0) y días previos.
8. **Resumen** + [Agendar cita] → `ReservaService.agendar`. Con `HorarioOcupadoException` vuelve al paso 6 con el
   mensaje y el formulario intacto. Al terminar: `sessionStatus.setComplete()`.

**Hecho cuando:** se agenda por teléfono para un paciente sin cuenta; se agenda desde una indicación y esta queda
`AGENDADA`.

### B7 · Inicio de recepción (2)

**Depende de:** B1, B5. **Prototipo:** `prototipo/recepcion-inicio.html`.

`GET /recepcion/inicio`: seis contadores, cada uno enlazado a su pantalla:

| Contador | Cálculo | Enlace |
| --- | --- | --- |
| Citas por confirmar hoy | filas de `confirmaciones` con fecha de hoy | `/recepcion/confirmaciones?fecha=hoy` |
| Cupos liberados sin cubrir | `abiertas().size()` | `/recepcion/cupos` |
| Citas indicadas pendientes | `pendientes().size()` | `/recepcion/pendientes` |
| Pacientes en sala | citas de hoy en `ASISTIO` sin `inicio_real` | `/recepcion/agenda` |
| Citas de ayer sin estado | citas de ayer en `PROGRAMADA` o `CONFIRMADA` | `/recepcion/agenda?fecha=ayer` |
| Citas de hoy sin llegada | citas de hoy ya iniciadas en `PROGRAMADA` o `CONFIRMADA` | `/recepcion/agenda` |

Debajo, una línea por médico con citas hoy: «Atendiendo», «Retrasado X min» (hay una cita `ASISTIO` sin `inicio_real` y
ya pasó su hora) o «Libre».

**Hecho cuando:** cada contador coincide con su pantalla de destino usando los datos de prueba.

### B8 · Pacientes (3)

**Depende de:** A1, A5. **Prototipo:** `prototipo/recepcion-pacientes.html`.

**Salida:** `service/PacienteService`, `controller/recepcion/PacienteController`, `templates/recepcion/pacientes.html`.

| Ruta | Hace |
| --- | --- |
| `GET /recepcion/pacientes?q=` | Búsqueda por documento, nombre o teléfono (`ILIKE`, máximo 50) |
| `GET /recepcion/pacientes/{id}` | Ficha |
| `POST /recepcion/pacientes` | Registrar paciente (tipo y número de documento únicos; si existe: «Ya existe un paciente con ese documento.») |
| `POST /recepcion/pacientes/{id}` | Editar datos y teléfono (recepción sí puede cambiar el teléfono) |
| `POST /recepcion/pacientes/{id}/cuentas/{idCuenta}/revocar` | `cuenta_paciente.revocado_en = ahora` |
| `POST /recepcion/citas/{idCita}/preferencia` | Editar o revocar con `PreferenciaService` |

Ficha: (1) datos personales y de contacto; (2) cuentas asociadas: la propia y las de terceros con su fecha de
consentimiento; (3) historial de citas con estado; (4) indicadores: tasa de no asistencia = `NO_ASISTIO / (REALIZADA +
NO_ASISTIO)`, tasa de cancelación = canceladas / total, veces que aceptó un cupo (llamadas `ACEPTO`), días desde la
última cita; (5) preferencias de disponibilidad editables; (6) indicaciones pendientes y vencidas. **Sin información
clínica.**

**Hecho cuando:** la ficha de un paciente con citas de dos cuentas distintas muestra ambas cuentas y todas sus citas.

### B9 · Agenda del médico (6)

**Depende de:** A6, A10, B5 (`AgendaService`). **Prototipo:** `prototipo/medico-agenda.html`.

**Regla de oro:** toda consulta filtra por `usuario.getIdMedico()` de la sesión. **No hay selector de médico.** Si se
pide una cita de otro médico → 404.

| Ruta | Hace |
| --- | --- |
| `GET /medico/agenda?fecha=&estado=&modalidad=&cita=` | Vista día del médico de la sesión; las citas + los huecos libres del horario |
| `POST /medico/citas/{id}/iniciar` · `/finalizar` · `/no-asistio` | `iniciarAtencion`, `finalizarAtencion`, `marcarNoAsistio` |
| `POST /medico/citas/{id}/cancelar` | `cancelarPorClinica` con motivo obligatorio |
| `POST /medico/citas/{id}/duracion` | `ajustarDuracion` (solo si `tipo.ajuste_medico`) |
| `POST /medico/citas/{id}/indicaciones` | `guardarIndicaciones` (texto que verá el paciente) |
| `POST /medico/citas/{id}/indicacion` | `CitaIndicadaService.indicar` → queda `PENDIENTE` |
| `GET/POST /medico/citas/{id}/agendar-ahora?paso=1..4` | Ver abajo |

- [Marcar no asistió] se muestra deshabilitado con `title="Disponible desde HH:mm"` mientras
  `ventana(cita, NO_ASISTIO)` no lo permita.
- «Siguiente cita»: casilla que despliega tipo, duración (prellenada con la del médico para ese tipo), ventana
  recomendada (`1 semana`, `15 días`, `1 mes`, `3 meses`, `otra fecha` → `fechaRecomendada = hoy + eso`), vigencia
  (prellenada con `hoy + vigencia_indicada_dias`) e indicaciones.
- **[Agendar ahora]**, 4 pasos: (1) confirmar tipo y duración; (2) horarios del propio médico (`ofertasProximas`, todos
  visibles, `PERFECTO` destacados) y bloqueo temporal con contador; (3) casilla obligatoria «El paciente autoriza
  verbalmente esta cita»; (4) [Confirmar] → crea la indicación y llama a `ReservaService.agendar` con canal
  `CONSULTA_MEDICA`, `idCuentaTitular` = el de la cita de origen, `autorizacionVerbal = true` e `idIndicacion`.

**Hecho cuando:** `AgendaMedicoControllerTest` (`@WebMvcTest`): un médico que pide `?cita=` de otro médico recibe 404;
iniciar y finalizar dejan la cita `REALIZADA`; [Agendar ahora] crea la cita con canal `CONSULTA_MEDICA` y la
indicación `AGENDADA`.

---

## 11. Paquete C — 20 %

**Acceso, portal del paciente e inicio del médico.** 28 puntos.

### C1 · Inicio de sesión real (4)

**Depende de:** A1, A2. **Prototipo:** `prototipo/login.html`, `prototipo/publico.html`.

**Qué se espera:** cada rol entra a su inicio; tras varios intentos fallidos la cuenta se bloquea unos minutos.

**Salida:**
- `config/UsuarioSesion` (§7.3). `getAuthorities()` = `ROLE_` + rol. `isEnabled()` = estado distinto de `BAJA`.
  `isAccountNonLocked()` = estado distinto de `BLOQUEADA` **y** (`bloqueado_hasta` nulo o anterior a ahora).
- `service/UsuarioDetallesService implements UserDetailsService`: busca la cuenta por correo (en minúsculas y sin
  espacios); si no existe → `UsernameNotFoundException`.
- `service/CuentaService`:
  - `registrarAccesoCorrecto(idCuenta)`: `intentos_fallidos = 0`, `bloqueado_hasta = null`, auditoría `LOGIN_OK`.
  - `registrarIntentoFallido(correo)`: si la cuenta existe, `intentos_fallidos + 1`; al llegar a
    `parametros().maxIntentosLogin` → `bloqueado_hasta = ahora + minutosBloqueoLogin`, `intentos_fallidos = 0` y
    auditoría `CUENTA_BLOQUEADA`. Siempre auditoría `LOGIN_FALLIDO` (`id_cuenta` nulo si el correo no existe).
  - `cerrarSesiones(String correo)`: con `FindByIndexNameSessionRepository`, borra todas las sesiones de ese usuario
    (lo usan C3, C4 y D4).
- `config/ManejadorLoginExitoso` y `ManejadorLoginFallido`. Destino por rol: `PACIENTE` → `/paciente/inicio`,
  `MEDICO` → `/medico/inicio`, `RECEPCION`, `ADMINISTRADOR` y `SUPERADMIN` → `/recepcion/inicio`. Fallo por bloqueo
  (`LockedException`) → `/login?bloqueado`; otro fallo → `/login?error`.
- `SeguridadConfig`: `loginPage("/login")`, `usernameParameter("correo")`, `passwordParameter("contrasena")`, los dos
  manejadores; añadir `/login`, `/error`, `/registro` y `/recuperar/**` a las rutas públicas. Jerarquía de roles como
  `@Bean RoleHierarchy`: `ROLE_SUPERADMIN > ROLE_ADMINISTRADOR > ROLE_RECEPCION`, de modo que `/recepcion/**` queda con
  `hasRole("RECEPCION")` y `/admin/**` con `hasRole("ADMINISTRADOR")` y el superadministrador entra a ambas. (Si alguna
  regla `hasRole` no respetara la jerarquía, se escribe con `hasAnyRole` en esa regla.)
- `controller/publico/LoginController` (`GET /login`), `templates/publico/login.html`, y `templates/publico/inicio.html`
  rehecha desde `prototipo/publico.html` (botones Iniciar sesión, Registrarse, Buscar cita → `/login`).
- Mensajes: `?error` → «Correo o contraseña incorrectos.» · `?bloqueado` → «Tu cuenta está bloqueada temporalmente.
  Inténtalo de nuevo en unos minutos.» · `?registrado` → «Cuenta creada. Ya puedes iniciar sesión.» ·
  `?contrasenaCambiada` → «Contraseña actualizada. Inicia sesión con la nueva.»

**Hecho cuando:** `LoginTest` (`@SpringBootTest` + Testcontainers + `MockMvc` con `formLogin()`): el paciente de prueba
entra y va a `/paciente/inicio`; un paciente que pide `/medico/inicio` recibe 403; 5 contraseñas erróneas bloquean la
cuenta e incluso la correcta falla hasta que pasa el tiempo. `InicioControllerTest` sigue pasando (si necesita beans
nuevos, se simulan con `@MockitoBean`).

### C2 · Registro (3)

**Depende de:** C1. **Prototipo:** `prototipo/registro.html`.

**Salida:** `dto/RegistroForm`, `service/RegistroService`, `controller/publico/RegistroController` (`GET/POST
/registro`), `templates/publico/registro.html`.

**Formulario** (longitudes = columnas de `cuenta` y `paciente`): `correo` (`@Email`, máx. 120), `contrasena` (8 a 72),
`confirmacion` (igual a la contraseña), `tipoDocumento` (`TipoDocumento`), `nroDocumento` (máx. 20), `nombres` y
`apellidos` (máx. 80), `fechaNacimiento` (`@Past`), `telefono` (obligatorio, máx. 20), `aceptaTerminos`
(`@AssertTrue`).

**`RegistroService.registrar(form)`** (`@Transactional`):
1. Correo ya usado → «Ese correo ya tiene una cuenta. ¿Olvidaste tu contraseña?».
2. Buscar paciente por (`tipo_documento`, `nro_documento`):
   - **No existe** → crear `paciente` (con su teléfono) y `cuenta` (rol `PACIENTE`, `ACTIVA`, `intentos_fallidos = 0`,
     contraseña con `PasswordEncoder`, `id_paciente_propio` = el nuevo).
   - **Existe y ya es paciente propio de otra cuenta** → «Ya existe una cuenta con estos datos. Recupera tu
     contraseña.».
   - **Existe sin cuenta** (lo registró recepción) → se vincula solo si coinciden la fecha de nacimiento **y** el
     teléfono (comparando solo dígitos); auditoría `VINCULO_PACIENTE`. Si algo no coincide: «No pudimos validar tus
     datos. Comunícate con el consultorio.» (**el mensaje no revela si el documento existe**).
3. Redirige a `/login?registrado`.

**Hecho cuando:** `RegistroServiceTest` cubre los cuatro casos y comprueba que la contraseña guardada empieza por `$2`
(BCrypt).

### C3 · Recuperación de contraseña (3)

**Depende de:** C1. **Prototipo:** `prototipo/recuperar.html`, `prototipo/cambiar-contrasena.html`.

**Qué se espera:** un enlace de un solo uso que **solo permite cambiar la contraseña** y **no inicia sesión**.

**Entradas:** `application.properties`:

```properties
app.recuperacion.secreto=${RECUPERACION_SECRETO:solo-para-desarrollo-cambiar-en-produccion}
app.recuperacion.minutos=30
```

**Salida:** `service/RecuperacionService`, `controller/publico/RecuperacionController`, dos plantillas.

| Ruta | Hace |
| --- | --- |
| `GET/POST /recuperar` | Pide el correo. **Siempre** responde «Si el correo está registrado, te enviamos un enlace para cambiar tu contraseña.» |
| `GET /recuperar/cambiar?token=` | Formulario de nueva contraseña (si el token es inválido, mensaje y botón para pedir otro) |
| `POST /recuperar/cambiar` | `token`, `contrasena`, `confirmacion` → canje → `/login?contrasenaCambiada` |

- **Emitir** (solo si la cuenta existe y está `ACTIVA`): `jti = UUID`, `aud = "recuperar-contrasena"`,
  `emitido_en = ahora`, `expira_en = ahora + 30 min`; fila en `token_recuperacion` con `revocado = false`.
  Token = `jti.exp.firma`, con `exp` en segundos epoch y `firma` = HMAC-SHA256(secreto, `jti + "." + aud + "." + exp`)
  en Base64 URL sin relleno. **En el MVP no se envía correo**: el enlace `http://localhost:8080/recuperar/cambiar?token=…`
  se escribe en el log con nivel INFO.
- **Canjear:** firma correcta (comparar con `MessageDigest.isEqual`), `exp` futuro, la fila existe con el mismo `aud`,
  `consumido_en` nulo y `revocado = false`, y la cuenta sigue `ACTIVA`. Entonces: nueva contraseña,
  `consumido_en = ahora`, los demás tokens de la cuenta `revocado = true`, `intentos_fallidos = 0`,
  `bloqueado_hasta = null` y `CuentaService.cerrarSesiones(correo)`. Cualquier fallo → «El enlace caducó o ya fue
  usado. Solicita uno nuevo.».

**Hecho cuando:** `RecuperacionServiceTest`: un token sirve una vez y el segundo uso falla; un token con un carácter de la
firma cambiado falla; uno vencido (con `Clock` adelantado) falla; tras canjear no hay sesión iniciada.

### C4 · Mi perfil del paciente (2)

**Depende de:** C1. **Prototipo:** `prototipo/paciente-perfil.html`.

| Ruta | Hace |
| --- | --- |
| `GET /paciente/perfil` | Correo, nombres, documento y fecha de nacimiento (solo lectura), teléfono |
| `POST /paciente/perfil/datos` | Cambia el **teléfono** (del paciente propio) y el **correo** (único) |
| `POST /paciente/perfil/contrasena` | `actual`, `nueva`, `confirmacion` → revoca sus tokens, cierra **todas** sus sesiones y redirige a `/login?contrasenaCambiada` |

El paciente solo puede editar teléfono, correo y contraseña. «Cerrar sesión» es un formulario `POST /logout` (lo pone D1
en la barra lateral).

**Hecho cuando:** cambiar la contraseña con la actual equivocada falla; con la correcta obliga a volver a entrar.

### C5 · Inicio del paciente, avisos y WhatsApp (2)

**Depende de:** C1, A10, D2. **Prototipo:** `prototipo/paciente-inicio.html`, `paciente-avisos.html`,
`paciente-whatsapp.html`.

**Salida:** `service/PortalService` (consultas del portal; lo amplía C7), tres controladores en `controller/paciente/`,
tres plantillas.

- `GET /paciente/inicio`: saludo con el nombre, las 3 próximas citas visibles para la cuenta (regla de C7), los avisos
  activos y, por cada indicación pendiente y vigente del paciente propio, la alerta «{nombres apellidos del médico}
  indicó una cita de {tipo}. Vigente hasta el {dd/MM}.» con botón [Agendar seguimiento] →
  `/paciente/cita/nueva?indicacion={id}`.
- `GET /paciente/avisos`: tarjetas de `aviso` con `activo = true` (banner, título, texto, botón «Conocer más» con
  `target="_blank" rel="noopener"`).
- `GET /paciente/whatsapp`: opciones del prototipo + «Otro». Cada una abre
  `https://wa.me/{whatsapp_numero}?text={mensaje codificado}` con el mensaje «Hola, soy {nombres} {apellidos}
  ({tipo} {número de documento}). {texto de la opción}»; «Otro» usa «Deseo contactar con un agente». Si
  `whatsapp_numero` es nulo, la pantalla dice que el canal no está disponible (y D1 oculta la entrada del menú).

**Hecho cuando:** con `whatsapp_numero` configurado, el enlace contiene el nombre y el documento codificados.

### C6 · Nueva cita del paciente (5)

**Depende de:** A4, A5, A10 (con las cáscaras se puede empezar antes). **Prototipo:**
`prototipo/paciente-nueva-cita-1.html` a `-5.html`.

**Qué se espera:** el flujo más importante del portal, en 5 pasos, sin perder datos si el horario se ocupa.

**Salida:** `dto/NuevaCitaPacienteForm` y `dto/TerceroForm` (`Serializable`, en sesión con `@SessionAttributes`),
`controller/paciente/NuevaCitaController`, `templates/paciente/nueva-cita-{1..5}.html`, y en `RegistroService` el
método `vincularTercero(idCuenta, TerceroForm)`.

| Paso | Ruta | Qué pasa |
| --- | --- | --- |
| 1 · ¿Para quién? | `GET/POST /paciente/cita/nueva` | «Para mí» → paciente propio. «Para otra persona» → `TerceroForm`. **Desde una indicación este paso no se muestra**: el paciente ya está fijado |
| 1b · Otra persona | `POST /paciente/cita/tercero` | Ver «Tercero» abajo |
| 2 · ¿Cómo buscar? | `GET/POST /paciente/cita/medico` | Por médico (búsqueda por nombre + médicos de sus citas recientes) o por especialidad (fecha opcional → médicos con horarios ese día). **Desde una indicación**: solo `medicosQueAtienden(tipo indicado)`, con el que la indicó primero y marcado «Te lo indicó» |
| 3 · Cita indicada | `GET/POST /paciente/cita/indicada` | Solo si `pendienteDePacienteParaMedico(paciente, médico elegido)` existe, aunque el médico elegido no sea quien la indicó: [Agendar el seguimiento indicado] (tipo y duración de la indicación; si lo indicó otro médico se dice «Indicada por {médico}») o [Agendar una consulta nueva] (`id_tipo_web_defecto`) |
| 4 · Horarios | `GET /paciente/cita/horarios?fecha=&modalidad=` | `ofertasProximas`; pestañas Presencial / Teleconsulta según el tipo; filtro de fecha con [Restablecer] |
| 4b · Elegir hora | `POST /paciente/cita/bloqueo` | `BloqueoTemporalService.tomar` → paso 5 |
| 5 · Resumen | `GET /paciente/cita/resumen` | Datos de la cita, contador «Este horario está reservado para ti por m:ss», bloque de disponibilidad inmediata, términos |
| Agendar | `POST /paciente/cita` | `ReservaService.agendar` con canal `WEB`, `idCuentaTitular` = la cuenta de la sesión |

- **Desde una indicación** (`/paciente/cita/nueva?indicacion={id}`, botón del inicio): se comprueba que está pendiente
  y vigente y que su paciente (el de la cita de origen) es el paciente propio de la cuenta o un tercero vinculado a ella
  (`cuenta_paciente` sin revocar); si no → 404. Se fijan **paciente, tipo y duración** de la indicación, no se ofrece
  «Para otra persona», y se entra al paso 2 con solo los médicos que atienden ese tipo (el que la indicó primero);
  después, paso 4. Una indicación no tiene fecha tope para la cita: solo su vigencia limita hasta cuándo se agenda.
- **Médicos que aparecen:** `activo = true`, con `medico_tipo_consulta` para el tipo que se va a agendar (un médico sin
  duraciones no aparece). Si el médico tiene varias especialidades y se buscó por médico, el paso 2 pide elegir una.
- **Duración:** la de `medico_tipo_consulta.duracion_configurada`; si viene de una indicación, la de la indicación.
- **Tercero:** tipo y número de documento, nombres, apellidos, fecha de nacimiento, teléfono (opcional) y la casilla
  obligatoria «Declaro tener el consentimiento del paciente para agendar citas a su nombre». Si el documento ya existe:
  se usa ese paciente sin cambiar sus nombres, y si la fecha de nacimiento no coincide → «Revisa los datos o
  comunícate con el consultorio.». **El teléfono que escribe la cuenta nunca se guarda en el paciente**; si es distinto
  del registrado, aviso «Para actualizar el teléfono del paciente, debe comunicarse con recepción.» (sin mostrar el
  registrado) y se sigue. Se crea `cuenta_paciente` con `consentimiento_en = ahora` si no existe; si está revocado →
  «Este paciente retiró su consentimiento. Comunícate con el consultorio.». Auditoría `VINCULO_PACIENTE`.
- **Disponibilidad inmediata** (paso 5): casilla «Acepto ser llamado si se libera un cupo antes» → tres campos
  obligatorios: alcance (este médico / toda la especialidad), tiempo mínimo de llegada (valor + unidad, se guarda en
  minutos; en teleconsulta se sugiere 0), días previos. Se envía como `PreferenciaDatos`.
- **Horario ocupado:** `catch (HorarioOcupadoException e)` → flash `error` y `redirect:/paciente/cita/horarios`; el
  formulario sigue en sesión. Al terminar: `sessionStatus.setComplete()` y redirect a `/paciente/citas` con «Cita
  agendada.».

**Hecho cuando:** se agenda para sí mismo y para un tercero nuevo; con otra sesión que agenda el mismo horario antes de
confirmar, se vuelve a horarios con el mensaje y los datos del paso 5 conservados.

### C7 · Mis citas (4)

**Depende de:** A5, A6. **Prototipo:** `prototipo/paciente-mis-citas.html`, `paciente-citas-pasadas.html`.

**Qué ve una cuenta:** **todas** las citas de su paciente propio (las agendara quien las agendara) **más** las que ella
misma agendó para terceros (`cita.id_cuenta_titular = cuenta`). Nunca las que un tercero tenga por otras vías.

| Ruta | Hace |
| --- | --- |
| `GET /paciente/citas` | Próximas (`inicio >= ahora`, estado `PROGRAMADA`, `CONFIRMADA` o `ASISTIO`). Sin citas: «Usted no tiene próximas citas» |
| `GET /paciente/citas/pasadas` | El resto, de la más reciente a la más antigua, con su estado |
| `POST /paciente/citas/{id}/cancelar` | Comprueba que la cuenta ve la cita (si no, 404) → `cancelarPorPaciente(id, cuenta, null)` |
| `POST /paciente/citas/{id}/preferencia` | Guardar (`PreferenciaService.guardar`) |
| `POST /paciente/citas/{id}/preferencia/revocar` | `PreferenciaService.revocar` |

- Tarjeta: paciente (solo si no es el propio), especialidad, médico, fecha y hora, estado; tres botones visibles sin
  expandir: [Expandir], [Reprogramar] (abre C9; solo si la cita está `PROGRAMADA` o `CONFIRMADA` y aún no empezó) y
  [Cancelar]. Debajo de la lista: «Reprogramar mueve la misma cita a otra fecha u hora, sin plazo. Cancelar la anula.»
- Expandir: datos de la cita + indicaciones del médico + indicaciones administrativas; si no hay ninguna: «No hay
  indicaciones u observaciones para esta cita». Incluye ver, editar y revocar la disponibilidad inmediata.
- Cancelar: modal con los datos, «Puedes cancelar hasta las HH:mm del dd/MM» (`ventana(cita,
  CANCELADA_POR_PACIENTE).hasta()`), el texto de penalización si `mostrar_penalizacion` es verdadero, y botones
  [Cancelar cita] / [Mantener cita]. **No existe botón de confirmar.**

**Hecho cuando:** una cuenta que agendó para un tercero ve esa cita, pero no otra del mismo tercero agendada por
recepción; el paciente propio ve las dos.

### C8 · Inicio del médico (2)

**Depende de:** A6, A10. **Prototipo:** `prototipo/medico-inicio.html`.

`GET /medico/inicio`, todo filtrado por el médico de la sesión:
1. Contadores de hoy: programadas (`PROGRAMADA`), confirmadas (`CONFIRMADA`), atendidas (`REALIZADA`), pendientes
   (`PROGRAMADA`, `CONFIRMADA` o `ASISTIO`).
2. Próxima cita: hora, paciente, tipo, modalidad.
3. Puntualidad: con la última cita de hoy que tenga `inicio_real`, «Vas X min atrasado» o «Vas X min adelantado»
   (`inicio_real − inicio`).
4. Alertas: indicaciones suyas sin agendar (`pendientesDeMedico`, con su vigencia) y cambios de horario rechazados
   (auditoría `CAMBIO_HORARIO_RECHAZADO` de su cuenta en los últimos 7 días), cada una enlazada a su pantalla.

**Hecho cuando:** con datos de prueba los contadores coinciden con la agenda del día.

### C9 · Reprogramar desde Mis citas (3)

**Depende de:** A4, A7, C6 (reutiliza la pantalla de horarios y el bloqueo temporal), C7. **Prototipo:**
`prototipo/paciente-reprogramar-1.html`, `prototipo/paciente-reprogramar-2.html`.

**Qué se espera:** el paciente mueve una de sus citas a otro horario **del mismo médico**, en dos pasos y **sin plazo de
antelación**. La cita conserva su id, vuelve a `PROGRAMADA` y el consultorio la volverá a llamar para confirmarla. Si el
horario se ocupa mientras decide, no pierde nada de lo que eligió.

**Entradas:** la cita (la cuenta debe verla según la regla de C7; estado `PROGRAMADA` o `CONFIRMADA`; `inicio > ahora`);
médico, tipo, duración y modalidad **fijos**, tomados de la cita; `DisponibilidadService.ofertasProximas(idMedico,
duracion, desde, idCuenta)`; su preferencia de disponibilidad inmediata vigente, si la tiene.

**Salida:** `dto/ReprogramarForm` (`Serializable`, en sesión con `@SessionAttributes`: `idCita`, `nuevoInicio`,
`preferencia`), `controller/paciente/ReprogramarController`, `templates/paciente/reprogramar-1.html` y
`reprogramar-2.html`.

| Paso | Ruta | Qué pasa |
| --- | --- | --- |
| 1 · Nuevo horario | `GET /paciente/citas/{id}/reprogramar?fecha=` | Cabecera con la cita actual (fecha, hora, modalidad, paciente, estado). `ofertasProximas` del mismo médico con la duración de la cita (tres fechas desde `fecha`, hoy por defecto; filtro de fecha con [Restablecer]); solo la pestaña de la modalidad de la cita. Textos: «Se conservan el médico, el tipo de consulta, la duración y la modalidad.» · «Puedes elegir cualquier horario disponible: no hay plazo para reprogramar.» · «Tu horario actual quedará libre para otros pacientes.» |
| 1b · Elegir hora | `POST /paciente/citas/{id}/reprogramar/bloqueo` | `nuevoInicio` → `BloqueoTemporalService.tomar(cuenta, medico, inicio, inicio + D)`; el contador «reservado para ti por m:ss» aparece junto al horario elegido con «Tu cita actual sigue en pie hasta que confirmes el cambio.» → [Continuar] |
| 2 · Confirmar | `GET /paciente/citas/{id}/reprogramar/confirmar` | «Horario actual → Nuevo horario», paciente, médico, tipo y duración, modalidad (y dirección si es presencial), teléfono de contacto (`ContactoService.telefonoDe`), aviso «Al reprogramar, tu cita vuelve a quedar programada y el consultorio te llamará otra vez para confirmarla. Es la misma cita: conserva sus indicaciones y su historial.», bloque de disponibilidad inmediata **prellenado** con la preferencia vigente (editable; si no tenía, la casilla para crearla), contador del bloqueo. Botones [Reprogramar cita] · [Elegir otro horario] · [Mantener el horario actual] |
| Reprogramar | `POST /paciente/citas/{id}/reprogramar` | `ReprogramacionService.reprogramar(id, nuevoInicio, cuenta)`; si la preferencia cambió → `PreferenciaService.guardar` (no toca `inscrito_en`: «Tu antigüedad en la lista no cambia»); si la desmarcó → `revocar`; `BloqueoTemporalService.liberar(cuenta)`; `sessionStatus.setComplete()` → `/paciente/citas` con «Cita reprogramada. El consultorio te llamará para confirmarla; tu horario anterior ya está disponible para otros pacientes.» |
| Mantener | `POST /paciente/citas/{id}/reprogramar/mantener` | `BloqueoTemporalService.liberar(cuenta)`, limpia la sesión → `/paciente/citas` sin cambios |

- **Permisos:** la misma comprobación de visibilidad que C7 (si la cuenta no ve la cita → 404). Si la cita ya no está en
  `PROGRAMADA` o `CONFIRMADA`, o ya empezó, el botón no se muestra en Mis citas y el `POST` responde con el flash
  «Esta cita ya no se puede reprogramar.» y vuelve a `/paciente/citas`.
- **Horario ocupado:** `catch (HorarioOcupadoException e)` → flash `error` «Ese horario acaba de ser ocupado. Tu cita
  actual sigue en pie; actualizamos los horarios disponibles y conservamos lo que ingresaste.» y
  `redirect:/paciente/citas/{id}/reprogramar`; el formulario sigue en sesión.
- No se cambia de médico ni de tipo de consulta. Pie de ambas pantallas: «Reprogramar mueve esta misma cita a otro
  horario. Si quieres cambiar de médico o de tipo de consulta, cancela la cita y agenda una nueva.»
- Vale igual para una cita agendada desde una indicación de seguimiento: tampoco tiene fecha tope.

**Hecho cuando:** `ReprogramarControllerTest` (`@SpringBootTest` + Testcontainers + `MockMvc`): una cita `CONFIRMADA`
reprogramada conserva su `id_cita`, queda `PROGRAMADA` y tiene un evento `REPROGRAMACION`; su preferencia conserva
`inscrito_en`; si otra sesión toma el horario antes de confirmar, se vuelve al paso 1 con el mensaje y la selección
conservada; una cuenta que no ve la cita recibe 404; una cita ya iniciada no muestra el botón y el `POST` la rechaza.

---

## 12. Paquete D — 15 %

**Plantilla común, datos iniciales, administración, superadministrador y reportes.** 22 puntos. D1 y D2 son de la fase 1:
los demás dependen de ellas.

### D1 · Plantilla visual común y barra lateral (3)

**Depende de:** nada para empezar (usa `UsuarioSesion` cuando C1 se fusione). **Prototipo:** la estructura de
`prototipo/paciente-inicio.html` y el menú de `prototipo/assets/app.js`.

**Salida:**
- `templates/fragmentos/base.html` con tres fragmentos:
  - `head(titulo)`: `meta` charset y viewport, `<title>{titulo} · {nombre del consultorio}</title>`, Bootstrap 5.3.3,
    Bootstrap Icons 1.11.3 (los mismos CDN del prototipo) y `/css/app.css`.
  - `mensajes`: alertas Bootstrap descartables para los flash `exito` (verde), `error` (rojo), `aviso` (amarillo).
  - `scripts`: `bootstrap.bundle.min.js` 5.3.3 y `/js/app.js`.
- `templates/fragmentos/barra.html` :: `barra(seccion)`: la barra lateral del prototipo, dibujada en el servidor según el
  rol de la sesión (`sec:authorize="hasRole('…')"`), con la entrada `seccion` marcada `active`, el nombre
  (`UsuarioSesion.nombreMostrado`) y el rol arriba, y abajo «Mi perfil» (solo paciente) y «Cerrar sesión» (formulario
  `POST /logout`). Menús en §13.5.
- `static/js/app.js`: copiar del prototipo **solo** `iniciarCuentasRegresivas` (`<span data-cuenta-regresiva="299">`
  muestra m:ss) e `iniciarAlternancias` (`data-muestra` / `data-oculta`). El menú ya no se dibuja con JavaScript.
- `static/css/app.css`: la regla de `#barra-lateral` del prototipo (sin `.indice-prototipo`).
- `config/AtributosGlobales` (`@ControllerAdvice`): `@ModelAttribute("consultorio")` con
  `app.consultorio.nombre=Consultorio San Isidro` (nueva propiedad) y `@ModelAttribute("whatsappDisponible")`
  (`whatsapp_numero` no nulo).
- `templates/fragmentos/ejemplo.html`: una página vacía de muestra con el esqueleto que copian todos:

```html
<!doctype html>
<html lang="es" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:replace="~{fragmentos/base :: head('Inicio')}"></head>
<body class="bg-body-tertiary">
<div class="d-lg-flex">
  <aside th:replace="~{fragmentos/barra :: barra('inicio')}"></aside>
  <div class="flex-grow-1">
    <main class="container-xl p-3 p-lg-4">
      <div th:replace="~{fragmentos/base :: mensajes}"></div>
      <!-- contenido de la pantalla -->
    </main>
  </div>
</div>
<th:block th:replace="~{fragmentos/base :: scripts}"></th:block>
</body>
</html>
```

**Hecho cuando:** con los usuarios de prueba, cada uno ve su menú; el administrador ve el de recepción más
«Administración»; en el móvil la barra se abre con el botón de menú; «Cerrar sesión» funciona. (Lo propio del
superadministrador en la barra lo añade D7.)

### D2 · Datos iniciales, usuarios de prueba y lectura de configuración (2)

**Depende de:** A1 (para el cargador Java; el SQL no depende de nada).

**Salida:**
- `src/main/resources/db/migration/V2__datos_iniciales.sql` con los valores de §13.3. Las tablas con id `IDENTITY` se
  insertan **sin** id, y las referencias se resuelven con subconsultas:
  `(SELECT id_tipo_consulta FROM tipo_consulta WHERE nombre = 'Consulta')`.
- `config/DatosDemo` (`CommandLineRunner`, `@Profile("dev")`): si no hay ninguna cuenta, crea los usuarios de §13.6
  con `PasswordEncoder`, dos médicos con especialidad, duraciones (= duración por defecto de cada tipo activo) y horario
  vigente desde hoy, y dos avisos de ejemplo.
- `src/main/resources/application-dev.properties` (`logging.level.pe.consultorio.citas=DEBUG`).
- `service/ConfiguracionService` con los métodos de lectura de §7.4.

**Hecho cuando:** con una base vacía, `spring-boot:run` con perfil `dev` deja los 6 usuarios listos para entrar y
`ConfiguracionService.huecoMinimoUtil()` devuelve 30.

### D3 · Configuración del administrador (5)

**Depende de:** D1, D2, A2. **Prototipo:** `prototipo/admin-configuracion.html` (incluye el texto de ayuda de una línea
de cada parámetro: copiarlo).

**Salida:** métodos de escritura en `ConfiguracionService`, `controller/admin/ConfiguracionController`,
`templates/admin/configuracion.html` (cuatro pestañas).

| Ruta | Hace |
| --- | --- |
| `GET /admin/configuracion?pestana=tipos\|parametros\|plazos\|catalogos` | La pestaña pedida |
| `POST /admin/configuracion/tipos` y `/tipos/{id}` | Crear o editar tipo: nombre (único, máx. 60), duración por defecto, incremento propio (vacío = global), quién ajusta (médico / recepción), reservable por web, modalidad, activo |
| `POST /admin/configuracion/parametros` | Todos los campos de `parametro_consultorio` |
| `POST /admin/configuracion/plazos` | Solo `minutos_desde` y `minutos_hasta` de cada transición; `hasta_fin_dia` se muestra, no se edita |
| `POST /admin/configuracion/especialidades` y `/{id}` | Nombre (único), descripción, activo |
| `POST /admin/configuracion/avisos` y `/{id}` | Banner (URL), título (máx. 100), texto (máx. 300, con contador), enlace, activo |

- Las duraciones e incrementos se eligen en un `<select>` con múltiplos de 15 (15 a 240): no se pueden escribir otros
  valores.
- Validaciones que replican las restricciones de la base (§13.3): el texto de penalización es obligatorio si se muestra
  la penalización; el tipo web por defecto debe ser reservable por web; `minutos_hasta >= minutos_desde`.
- **Desactivar un tipo con citas futuras:** primero se muestra «Hay {n} citas futuras de este tipo» con las 5 más
  próximas y un botón [Desactivar igualmente] (`confirmar=true`). Las citas no cambian.
- Cada cambio → auditoría `CONFIG_CAMBIO` con detalle `campo: anterior → nuevo`.

**Hecho cuando:** cambiar el incremento a 20 es imposible desde la pantalla; un cambio de parámetro aparece en
`auditoria`; desactivar «Consulta» con citas futuras pide confirmación.

### D4 · Usuarios (4)

**Depende de:** D1, D2, C1 (`CuentaService.cerrarSesiones`). **Prototipo:** `prototipo/admin-usuarios.html`.

**Salida:** `service/UsuarioService`, `controller/admin/UsuarioController`, `templates/admin/usuarios.html`.

| Ruta | Hace |
| --- | --- |
| `GET /admin/usuarios?q=&rol=` | Lista de cuentas: correo, nombre, rol, estado, bloqueo temporal. Las cuentas `SUPERADMIN` **no aparecen** para un administrador (D7 las muestra al superadministrador) |
| `POST /admin/usuarios/medicos` | **Alta de médico** (ver abajo) |
| `POST /admin/usuarios/personal` | Alta de recepción o administración: correo, contraseña inicial, rol (`RECEPCION` o `ADMINISTRADOR`; `SUPERADMIN` solo lo permite D7), `nombre_visible` (obligatorio) |
| `POST /admin/usuarios/{id}/bloquear` | `estado = BLOQUEADA` + motivo + `cerrarSesiones` |
| `POST /admin/usuarios/{id}/desbloquear` | `estado = ACTIVA`, `bloqueado_hasta = null`, `intentos_fallidos = 0` |
| `POST /admin/usuarios/{id}/baja` | `estado = BAJA` + `cerrarSesiones` + sus tokens `revocado = true` |
| `POST /admin/usuarios/medicos/{idMedico}/activo` | Activa o desactiva al médico (deja de recibir citas nuevas) |
| `GET /admin/usuarios/{id}/accesos` | Últimas 50 filas de `auditoria` con `LOGIN_OK`, `LOGIN_FALLIDO`, `CUENTA_BLOQUEADA` |

**Alta de médico** (una transacción): correo, contraseña inicial, nombres, apellidos, al menos una especialidad y **una
duración para cada tipo de consulta activo** (obligatorias, múltiplos de 15). Crea `cuenta` (rol `MEDICO`), `medico`
(`activo = true`), sus `medico_especialidad` y sus `medico_tipo_consulta`. Sin duraciones no se puede guardar: así un
médico nunca aparece para agendar sin poder calcular horarios. Todo cambio → auditoría `CONFIG_CAMBIO`.

**Hecho cuando:** un médico nuevo, tras definir su horario en «Mi horario», aparece en la nueva cita del paciente; una
cuenta bloqueada no puede entrar y su sesión abierta se cierra.

### D5 · Reportes y exportación a Excel (3)

**Depende de:** A5, A8, A10, D1. **Prototipo:** `prototipo/admin-reportes.html`.

**Qué se espera:** el tablero con 4 métricas calculadas desde el historial y su descarga en `.xlsx` con Apache POI.

**Salida:** `service/MetricaService`, `service/ExportacionService`, `controller/admin/ReporteController`,
`templates/admin/reportes.html`.

Métricas para un rango `desde`–`hasta` (por defecto, los últimos 30 días):

| # | Métrica | Cálculo |
| --- | --- | --- |
| 1 | % de horarios liberados que se recuperaron | Oportunidades creadas en el rango y ya cerradas: `CUBIERTA / (CUBIERTA + EXPIRADA)`. No se cuentan `REEMPLAZADA` ni `ABIERTA` |
| 2 | Tasa de no asistencia | `NO_ASISTIO / (REALIZADA + NO_ASISTIO)` de las citas con inicio en el rango, por médico, por especialidad y por hora de inicio |
| 3 | % de citas indicadas que terminan agendadas | Indicaciones creadas en el rango: `AGENDADA / (AGENDADA + VENCIDA + DESCARTADA)` |
| 4 | Reprogramaciones por cita | Eventos `REPROGRAMACION` por `id_cita`: promedio, máximo y cuántas citas tienen 0, 1, 2 y 3 o más |

- `GET /admin/reportes?desde=&hasta=` muestra las cuatro.
- `GET /admin/reportes/exportar?desde=&hasta=` devuelve un `XSSFWorkbook` con una hoja por métrica (encabezado en
  negrita, columnas autoajustadas), con `Content-Type:
  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` y `Content-Disposition: attachment;
  filename="reporte-citas-{desde}-{hasta}.xlsx"`.

**Hecho cuando:** `ExportacionServiceTest` genera el libro, lo vuelve a leer con POI y encuentra 4 hojas con los valores
esperados para datos de prueba conocidos.

### D6 · Mis estadísticas del médico (2)

**Depende de:** A6, D1. **Prototipo:** `prototipo/medico-estadisticas.html`.

`GET /medico/estadisticas`, solo con citas del médico de la sesión. **Nunca datos de otros médicos ni comparativas.**

1. Por tipo de consulta: con las últimas `n_ventana_estimacion` citas `REALIZADA` (con `inicio_real` y `fin_real`),
   mediana y P75 de la duración real (`fin_real − inicio_real`, en minutos), la duración configurada y, si hay menos de
   N, el avance «12 de 30 citas».
2. Alerta de variabilidad cuando la mediana y el P75, redondeados hacia arriba al múltiplo de 15, caen en valores
   distintos: «Este tipo de consulta tiene duraciones muy variables; quizá convenga dividirlo en dos.»
3. Puntualidad (últimos 30 días): retraso promedio (`inicio_real − inicio`) y % de consultas iniciadas tarde
   (`inicio_real > inicio`).
4. Tasa de no asistencia de sus pacientes y minutos perdidos por cancelaciones e inasistencias (suma de duraciones), en
   los últimos 30 días.

`service/Estadistica` (Java puro): `mediana(List<Integer>)` y `percentil(List<Integer>, int p)` por rango más cercano.

**Hecho cuando:** `EstadisticaTest`: mediana de [20, 30, 40] = 30, de [20, 30] = 25; P75 de [10, 20, 30, 40] = 30.

### D7 · Superadministrador: modo total y «entrar como» (3)

**Depende de:** C1, D1, D4. **Prototipo:** no tiene pantalla propia; reutiliza `prototipo/admin-usuarios.html` y la barra.

**Qué se espera:** el rol `SUPERADMIN` es una cuenta técnica para desarrollo y soporte. Al iniciar sesión trabaja en
**modo limitado**: ve y hace exactamente lo mismo que un administrador, y además ve las cuentas `SUPERADMIN` en la lista
de usuarios (sin poder tocarlas). Cuando lo necesita activa el **modo total**, que le permite dos cosas más:
**entrar como cualquier cuenta** (ver el sistema con los ojos de ese paciente, médico o recepcionista y actuar en su
nombre) y **gestionar superadministradores** (crear, bloquear, desbloquear, dar de baja). El modo total se activa a
propósito, con contraseña, queda registrado y se nota en pantalla.

**Entradas:** la sesión (`UsuarioSesion.getRol() == SUPERADMIN`), un atributo de sesión `MODO_TOTAL` (instante de
activación; ausente = modo limitado) y la contraseña de la propia cuenta para activarlo.

**Salida:**
- `config/ModoTotal` (`@Component`): `boolean activo(HttpSession)`, `void activar(HttpSession)`, `void desactivar(HttpSession)`.
  Dura hasta desactivarlo o cerrar sesión.
- `controller/admin/ModoTotalController`:

| Ruta | Hace |
| --- | --- |
| `GET /admin/modo-total` | Explica qué habilita el modo total y pide la contraseña |
| `POST /admin/modo-total` | `contrasena` → se compara con `PasswordEncoder.matches` contra la cuenta de la sesión; correcta → `activar` + auditoría `MODO_TOTAL_ACTIVADO` → `/admin/usuarios`; incorrecta → mismo formulario con «Contraseña incorrecta.» (cuenta un intento fallido como en el login) |
| `POST /admin/modo-total/salir` | `desactivar` + auditoría `MODO_TOTAL_DESACTIVADO` → `/recepcion/inicio` |

- **Entrar como**, con el `SwitchUserFilter` de Spring Security (sin dependencias nuevas), como `@Bean` en
  `SeguridadConfig` añadido con `addFilterAfter(filtro, AuthorizationFilter.class)`: `switchUserUrl = /admin/entrar-como`
  (solo `POST`, parámetro `correo`), `exitUserUrl = /admin/volver` (solo `POST`), `successHandler` = el
  `ManejadorLoginExitoso` de C1 (así llega al inicio del rol suplantado y, al volver, al de recepción),
  `switchFailureUrl = /admin/usuarios?error`. Antes del filtro, la regla de `SeguridadConfig` para `/admin/entrar-como`
  exige `ROLE_SUPERADMIN` **y** modo total (`access(...)` que lee el atributo de sesión); `/admin/volver` solo exige la
  autoridad `ROLE_PREVIOUS_ADMINISTRATOR`, que el propio filtro añade mientras dura la suplantación. Se registra
  auditoría `ENTRAR_COMO` (detalle: correo destino) y `VOLVER_DE_ENTRAR_COMO`; lo que se haga entre medias queda a
  nombre de la cuenta suplantada, como en cualquier sesión. No se puede entrar como una cuenta `BLOQUEADA` ni `BAJA`.
- `templates/fragmentos/barra.html`: para `SUPERADMIN`, el rol se muestra «Superadministrador» y debajo del menú de
  administración va «Modo total» → `/admin/modo-total` (`shield-lock`). Con el modo activo, una insignia roja
  «MODO TOTAL» arriba y «Salir del modo total» (formulario `POST`). Si la sesión tiene `ROLE_PREVIOUS_ADMINISTRATOR`,
  una franja fija arriba: «Estás viendo el sistema como {nombre mostrado} · [Volver]» (formulario `POST /admin/volver`).
- `templates/admin/usuarios.html` y `UsuarioService` (mismo paquete D): para el superadministrador, la lista incluye las
  cuentas `SUPERADMIN`; con el modo total activo aparece en cada fila `ACTIVA` (salvo la propia) el botón «Entrar como»
  (`POST /admin/entrar-como`), el alta de personal ofrece también el rol `SUPERADMIN`, y bloquear / desbloquear / dar de
  baja funcionan sobre cuentas `SUPERADMIN` ajenas. **Nunca sobre la propia cuenta.** Cada una de esas acciones comprueba
  `modoTotal.activo(sesion)` **en el controlador**, no solo en la vista; si no está activo → 403 con el mensaje «Activa el
  modo total para esta acción.».
- `GET /admin/usuarios/{id}/accesos` (D4) muestra también `MODO_TOTAL_ACTIVADO`, `MODO_TOTAL_DESACTIVADO`,
  `ENTRAR_COMO` y `VOLVER_DE_ENTRAR_COMO`.

**Lo que el modo total no cambia:** las reglas de negocio valen para todos. Los plazos de las transiciones, la exclusión
de solapes, los múltiplos del incremento y la prohibición de editar o borrar eventos, llamadas y auditoría se aplican
igual al superadministrador, también cuando entra como otra cuenta. El bloqueo por intentos fallidos de inicio de sesión
también le aplica.

**Hecho cuando:** `SuperadminTest` (`@SpringBootTest` + Testcontainers + `MockMvc`): el superadministrador de prueba
entra y abre `/admin/usuarios` (200); sin modo total, `POST /admin/entrar-como` → 403; activa el modo total con su
contraseña, entra como el paciente de prueba y `GET /paciente/inicio` responde 200; `POST /admin/volver` lo devuelve a su
sesión; un `ADMINISTRADOR` no ve cuentas `SUPERADMIN` en la lista y `/admin/modo-total` le responde 403; la auditoría
tiene las cuatro acciones nuevas.

---

## 13. Datos de referencia

### 13.1 Valores de los CHECK (lo que acepta la base)

| Tabla.columna | Valores |
| --- | --- |
| `rol.nombre` | `PACIENTE`, `MEDICO`, `RECEPCION`, `ADMINISTRADOR` |
| `cuenta.estado` | `ACTIVA`, `BLOQUEADA` (manual, sin fin), `BAJA`. El bloqueo por intentos es `bloqueado_hasta`, con el estado en `ACTIVA` |
| `paciente.tipo_documento` | `DNI`, `CE`, `PASAPORTE`; único junto con `nro_documento` |
| `parametro_consultorio` | Una sola fila con `id_parametro = 1`; `incremento_min` múltiplo de 15; `politica_proteccion` ∈ `NUNCA`, `BLANDA`, `DURA` |
| `tipo_consulta.modalidad_permitida` | `PRESENCIAL`, `TELECONSULTA`, `AMBAS`; `duracion_defecto` múltiplo de 15 |
| `cita.estado` | `PROGRAMADA`, `CONFIRMADA`, `ASISTIO`, `REALIZADA`, `NO_ASISTIO`, `CANCELADA_POR_PACIENTE`, `CANCELADA_POR_CLINICA` |
| `cita.modalidad` | `PRESENCIAL`, `TELECONSULTA` |
| `cita.canal_origen` | `WEB`, `PRESENCIAL`, `TELEFONO`, `WHATSAPP`, `DERIVACION`, `CONSULTA_MEDICA` |
| `cita` (marcas) | `ASISTIO` y `REALIZADA` exigen `hora_llegada`; `REALIZADA` exige `inicio_real` y `fin_real`; `fin_real > inicio_real` |
| `cita_evento.tipo_evento` | `CREACION`, `CAMBIO_ESTADO`, `REPROGRAMACION`, `AJUSTE_DURACION`, `LLEGADA`, `INICIO_ATENCION`, `FIN_ATENCION` |
| `cita_evento` (reglas) | `estado_nuevo = CONFIRMADA` exige `id_llamada`; `CANCELADA_POR_CLINICA` y el retiro (`ASISTIO → NO_ASISTIO`) exigen `motivo` |
| `cita_indicada.estado` | `PENDIENTE`, `AGENDADA` (exige `id_cita_generada`), `VENCIDA`, `DESCARTADA` (exige `motivo_descarte`) |
| `preferencia_disponibilidad.alcance` | `MEDICO`, `ESPECIALIDAD`; una por cita; `consentido_en >= inscrito_en` |
| `oportunidad.estado` | `ABIERTA` (sin `cerrada_en`), `CUBIERTA` (exige `id_cita_cobertura`), `EXPIRADA`, `REEMPLAZADA` |
| `registro_llamada.tipo_cola` | `CONFIRMACION`, `CUPO_LIBERADO`, `CITA_PENDIENTE` (referencias y resultados en §13.4) |
| Horas (`cita`, `franja_horaria`, `excepcion_horario`, `bloqueo_temporal`, `oportunidad`) | Minutos múltiplos de 15 y segundos en 0; `fin > inicio` |
| `horario_version` | Un médico no puede tener dos versiones con vigencias que se crucen (`vigente_hasta` nulo = sin fin) |
| `franja_horaria.dia_semana` | 1 = lunes … 7 = domingo |

Las tablas `cita_evento`, `registro_llamada` y `auditoria` **solo admiten INSERT** desde el código: no se escriben
métodos que las actualicen o borren.

### 13.2 Transiciones de la cita

| Método | Desde | Hacia | Quién | Plazo (fila de `plazo_transicion`) | Efecto extra |
| --- | --- | --- | --- | --- | --- |
| `confirmar` | `PROGRAMADA` | `CONFIRMADA` | Recepción / admin, **solo desde la cola** | `CONFIRMADA`: desde que entra a la cola hasta el inicio | Evento con `id_llamada` |
| `cancelarPorPaciente` | `PROGRAMADA`, `CONFIRMADA` | `CANCELADA_POR_PACIENTE` | Paciente (sin motivo) o recepción (con motivo) | `CANCELADA_POR_PACIENTE`: hasta la hora de inicio | Libera el horario |
| `cancelarPorClinica` | `PROGRAMADA`, `CONFIRMADA` | `CANCELADA_POR_CLINICA` | Médico (las suyas), recepción, admin | Sin límite | Motivo obligatorio; libera el horario |
| `marcarLlegada` | `PROGRAMADA`, `CONFIRMADA` | `ASISTIO` | Recepción / admin | `ASISTIO`: desde 30 min antes hasta el fin del día | `hora_llegada`; evento `LLEGADA` |
| `iniciarAtencion` | `ASISTIO` | (igual) | Médico (las suyas) | — | `inicio_real`; evento `INICIO_ATENCION` |
| `finalizarAtencion` | `ASISTIO` con `inicio_real` | `REALIZADA` | Médico (las suyas) | `REALIZADA`: hasta el fin del día siguiente | `fin_real`; evento `FIN_ATENCION` |
| `marcarNoAsistio` | `PROGRAMADA`, `CONFIRMADA` | `NO_ASISTIO` | Recepción, médico (las suyas) | `NO_ASISTIO`: desde 5 min después del inicio hasta el fin del día | — |
| `marcarRetiro` | `ASISTIO` | `NO_ASISTIO` | Recepción / admin | `RETIRO`: hasta el fin del día | Motivo obligatorio |
| `reprogramar` | `PROGRAMADA`, `CONFIRMADA` | `PROGRAMADA` | Paciente (desde Mis citas, C9), recepción, admin, médico (las suyas) | Sin plazo; solo antes del inicio de la cita | Mismo id; evento `REPROGRAMACION`; libera el horario anterior |

Las cancelaciones y la reprogramación **no** son posibles desde `ASISTIO`, `REALIZADA` ni `NO_ASISTIO`.

### 13.3 Datos iniciales (`V2__datos_iniciales.sql`)

**`rol`:** `PACIENTE`, `MEDICO`, `RECEPCION`, `ADMINISTRADOR`, `SUPERADMIN`.

**`especialidad`:** Medicina General, Cardiología, Pediatría (activas).

**`tipo_consulta`:**

| nombre | duracion_defecto | ajuste_medico | ajuste_recepcion | reservable_web | modalidad_permitida |
| --- | --- | --- | --- | --- | --- |
| Consulta | 30 | sí | sí | **sí** | `AMBAS` |
| Primera cita | 45 | sí | sí | no | `PRESENCIAL` |
| Seguimiento | 30 | sí | sí | no | `AMBAS` |
| Procedimiento menor | 60 | no | no | no | `PRESENCIAL` |

(`incremento_min` nulo y `activo` verdadero en todos.)

**`parametro_consultorio`** (fila 1):

| Columna | Valor | Columna | Valor |
| --- | --- | --- | --- |
| `incremento_min` | 15 | `vigencia_indicada_dias` | 90 |
| `politica_proteccion` | `NUNCA` | `mostrar_penalizacion` | false |
| `minutos_protegidos` | 120 | `texto_penalizacion` | NULL |
| `ventana_liberacion_h` | 48 | `max_intentos_login` | 5 |
| `bloqueo_temporal_min` | 5 | `minutos_bloqueo_login` | 15 |
| `n_ventana_estimacion` | 30 | `whatsapp_numero` | NULL |
| `anticipacion_confirm_h` | 48 | `dias_recordar_sugerencia` | 7 |
| `id_tipo_web_defecto` | el id de «Consulta» | | |

**`plazo_transicion`:**

| transicion | minutos_desde | minutos_hasta | hasta_fin_dia |
| --- | --- | --- | --- |
| `NO_ASISTIO` | 5 | NULL | 0 |
| `RETIRO` | NULL | NULL | 0 |
| `CANCELADA_POR_PACIENTE` | NULL | 0 | NULL |
| `CANCELADA_POR_CLINICA` | NULL | NULL | NULL |
| `CONFIRMADA` | NULL | 0 | NULL |
| `ASISTIO` | -30 | NULL | 0 |
| `REALIZADA` | NULL | NULL | 1 |

### 13.4 Resultados de llamada por cola

| Cola | Referencias obligatorias | Resultados | Efecto |
| --- | --- | --- | --- |
| `CONFIRMACION` | `id_cita` | `CONFIRMO`, `NO_CONTESTO`, `CANCELO`, `REPROGRAMO`, `NUMERO_INVALIDO` | Confirma · suma intento · cancela por paciente · abre reprogramación · marca el número |
| `CUPO_LIBERADO` | `id_oportunidad`, `id_cita`, `posicion_lista` | `ACEPTO`, `RECHAZO`, `NO_CONTESTO`, `NO_PUEDE_LLEGAR`, `NUMERO_INVALIDO`, `OTRO` | `ACEPTO` adelanta la cita; el resto solo registra |
| `CITA_PENDIENTE` | `id_indicacion` | `AGENDO`, `NO_CONTESTO`, `NO_LE_INTERESA`, `NUMERO_INVALIDO` | Abre nueva cita · registra · descarta · marca el número |

Un número queda marcado como inválido mientras el teléfono vigente del contacto siga siendo el mismo que se marcó.

### 13.5 Menús de la barra lateral

| Perfil | Entradas (texto → ruta, icono de Bootstrap Icons) |
| --- | --- |
| Paciente | Inicio → `/paciente/inicio` (`house`) · Nueva cita → `/paciente/cita/nueva` (`plus-circle`) · Mis citas → `/paciente/citas` (`calendar-check`) · Avisos → `/paciente/avisos` (`megaphone`) · WhatsApp del consultorio → `/paciente/whatsapp` (`whatsapp`, solo si `whatsappDisponible`) |
| Médico | Inicio → `/medico/inicio` (`house`) · Mi agenda → `/medico/agenda` (`calendar3`) · Mi horario → `/medico/horario` (`clock`) · Mis estadísticas → `/medico/estadisticas` (`bar-chart`) |
| Recepción | Inicio → `/recepcion/inicio` (`house`) · Agenda → `/recepcion/agenda` (`calendar3`) · Nueva cita → `/recepcion/cita/nueva` (`plus-circle`) · Confirmaciones → `/recepcion/confirmaciones` (`telephone`) · Cupos liberados → `/recepcion/cupos` (`calendar2-plus`) · Citas pendientes → `/recepcion/pendientes` (`clipboard-check`) · Pacientes → `/recepcion/pacientes` (`people`) |
| Administrador | Las de recepción + separador «Administración» + Configuración → `/admin/configuracion` (`sliders`) · Usuarios → `/admin/usuarios` (`person-gear`) · Reportes → `/admin/reportes` (`graph-up`) |
| Superadministrador | Las del administrador + Modo total → `/admin/modo-total` (`shield-lock`); con el modo activo, insignia «MODO TOTAL» y «Salir del modo total» (D7) |

### 13.6 Usuarios de prueba (perfil `dev`)

Contraseña de todos: `Demo2026!`

| Correo | Rol | Datos |
| --- | --- | --- |
| `superadmin@consultorio.pe` | SUPERADMIN | `nombre_visible` «Superadministrador». Solo existe con el perfil `dev`; en una instalación real se crea a mano |
| `admin@consultorio.pe` | ADMINISTRADOR | `nombre_visible` «Administrador» |
| `recepcion@consultorio.pe` | RECEPCION | `nombre_visible` «Recepción» |
| `medico1@consultorio.pe` | MEDICO | Carmen Ramírez · Cardiología · lunes a viernes 08:00–13:00 y 15:00–19:00 |
| `medico2@consultorio.pe` | MEDICO | Andrés Ponce · Medicina General · lunes, miércoles y viernes 09:00–13:00 |
| `paciente@consultorio.pe` | PACIENTE | Lucía Vega Ríos · DNI 12345678 · nacida el 1990-05-14 · teléfono 987654321 |

### 13.7 Textos fijos para el usuario

| Situación | Texto |
| --- | --- |
| Horario tomado por otra persona | Ese horario ya fue ocupado. Elige otro. |
| Login incorrecto | Correo o contraseña incorrectos. |
| Cuenta bloqueada por intentos | Tu cuenta está bloqueada temporalmente. Inténtalo de nuevo en unos minutos. |
| Registro con datos que no coinciden | No pudimos validar tus datos. Comunícate con el consultorio. |
| Tercero con fecha de nacimiento distinta | Revisa los datos o comunícate con el consultorio. |
| Pedir recuperación | Si el correo está registrado, te enviamos un enlace para cambiar tu contraseña. |
| Enlace de recuperación inválido | El enlace caducó o ya fue usado. Solicita uno nuevo. |
| Sin próximas citas | Usted no tiene próximas citas |
| Cita sin indicaciones | No hay indicaciones u observaciones para esta cita |
| Indicación de otro paciente | Esta indicación es para otro paciente. |
| Horario que deja fuera citas | Este cambio deja fuera {n} citas agendadas. Reprográmalas primero. |
| Contador del bloqueo | Este horario está reservado para ti por m:ss |
| Horario tomado al reprogramar | Ese horario acaba de ser ocupado. Tu cita actual sigue en pie; actualizamos los horarios disponibles y conservamos lo que ingresaste. |
| Cita reprogramada | Cita reprogramada. El consultorio te llamará para confirmarla; tu horario anterior ya está disponible para otros pacientes. |

---

## 14. Lista de revisión antes de fusionar un PR

Quien revise el PR comprueba, antes de fusionar:

- [ ] La rama parte de `main` actualizado y `./mvnw test` pasa en el PR.
- [ ] Solo toca los archivos de su tarea (o añade métodos al final de un repositorio).
- [ ] Ningún controlador usa un repositorio; ningún servicio devuelve nombres de vista.
- [ ] Las vistas reciben `record`s de `dto/`, no entidades.
- [ ] Los métodos que escriben tienen `@Transactional`, y la cita se escribe junto con su `cita_evento`.
- [ ] La hora sale del `Clock`, no de `LocalDateTime.now()` a secas.
- [ ] Las consultas del médico filtran por el médico de la sesión, nunca por un parámetro de la URL.
- [ ] Ningún `UPDATE` ni `DELETE` sobre `cita_evento`, `registro_llamada` o `auditoria`.
- [ ] Los selectores de hora y duración solo ofrecen múltiplos del incremento.
- [ ] Ningún error de base de datos llega al usuario; `23P01` se traduce a «Ese horario ya fue ocupado».
- [ ] Las acciones del modo total y «entrar como» se comprueban en el servidor, no solo escondiendo el botón.
- [ ] Los comentarios se entienden sin documentos externos.
- [ ] La pantalla se parece a su archivo de `prototipo/` (capturas en el PR).
- [ ] Se cumple cada punto de «Hecho cuando».
