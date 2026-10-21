-- Esquema inicial del sistema de citas (PostgreSQL 16).
-- 25 tablas, claves, restricciones CHECK, UNIQUE y EXCLUDE, y claves foráneas.
-- Las horas se guardan en TIMESTAMP sin zona: el consultorio opera en una sola zona horaria.
-- Los horarios, duraciones y rangos van en múltiplos de 15 minutos (literal fijo porque un CHECK no puede leer parametro_consultorio).
-- Esta migración no se edita una vez fusionada: cualquier cambio de esquema va en una migración nueva (V3__..., V4__...).

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ROL: Roles del sistema.
CREATE TABLE rol (
    id_rol SMALLINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    nombre VARCHAR(30) NOT NULL UNIQUE,
    CONSTRAINT pk_rol PRIMARY KEY (id_rol)
);

-- CUENTA: Credenciales y estado de acceso.
CREATE TABLE cuenta (
    id_cuenta BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    correo VARCHAR(120) NOT NULL UNIQUE,
    nombre_visible VARCHAR(120),
    password_hash VARCHAR(255) NOT NULL,
    id_rol SMALLINT NOT NULL,
    id_paciente_propio BIGINT UNIQUE,
    estado VARCHAR(15) NOT NULL,
    intentos_fallidos SMALLINT NOT NULL,
    bloqueado_hasta TIMESTAMP,
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT pk_cuenta PRIMARY KEY (id_cuenta),
    CONSTRAINT chk_cuenta_estado CHECK (estado IN ('ACTIVA','BLOQUEADA','BAJA')),
    CONSTRAINT chk_cuenta_intentos CHECK (intentos_fallidos >= 0)
);

-- PACIENTE: Persona atendida.
CREATE TABLE paciente (
    id_paciente BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    tipo_documento VARCHAR(10) NOT NULL,
    nro_documento VARCHAR(20) NOT NULL,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    telefono VARCHAR(20),
    correo VARCHAR(120),
    CONSTRAINT pk_paciente PRIMARY KEY (id_paciente),
    CONSTRAINT uq_paciente_tipo_documento_nro_documento UNIQUE (tipo_documento, nro_documento),
    CONSTRAINT chk_paciente_tipo_doc CHECK (tipo_documento IN ('DNI','CE','PASAPORTE'))
);

-- CUENTA_PACIENTE: Pacientes terceros para los que una cuenta agenda, con consentimiento. La cuenta solo ve las citas que ella agendó.
CREATE TABLE cuenta_paciente (
    id_cuenta BIGINT NOT NULL,
    id_paciente BIGINT NOT NULL,
    consentimiento_en TIMESTAMP NOT NULL,
    revocado_en TIMESTAMP,
    CONSTRAINT pk_cuenta_paciente PRIMARY KEY (id_cuenta, id_paciente),
    CONSTRAINT chk_cp_revocacion CHECK (revocado_en IS NULL OR revocado_en >= consentimiento_en)
);

-- MEDICO: Profesional que atiende.
CREATE TABLE medico (
    id_medico BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cuenta BIGINT NOT NULL UNIQUE,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    activo BOOLEAN NOT NULL,
    CONSTRAINT pk_medico PRIMARY KEY (id_medico)
);

-- TOKEN_RECUPERACION: Control de un solo uso del token de recuperación.
CREATE TABLE token_recuperacion (
    jti CHAR(36) NOT NULL,
    id_cuenta BIGINT NOT NULL,
    aud VARCHAR(40) NOT NULL,
    emitido_en TIMESTAMP NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    consumido_en TIMESTAMP,
    revocado BOOLEAN NOT NULL,
    CONSTRAINT pk_token_recuperacion PRIMARY KEY (jti),
    CONSTRAINT chk_token_vigencia CHECK (expira_en > emitido_en)
);

-- AUDITORIA: Bitácora persistente. Sin UPDATE ni DELETE.
CREATE TABLE auditoria (
    id_auditoria BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cuenta BIGINT,
    accion VARCHAR(40) NOT NULL,
    entidad VARCHAR(40) NOT NULL,
    id_registro VARCHAR(60),
    detalle TEXT,
    motivo VARCHAR(255),
    fecha_hora TIMESTAMP NOT NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (id_auditoria)
);

-- PARAMETRO_CONSULTORIO: Parámetros globales. Una sola fila.
CREATE TABLE parametro_consultorio (
    id_parametro SMALLINT NOT NULL,
    incremento_min SMALLINT NOT NULL,
    politica_proteccion VARCHAR(10) NOT NULL,
    minutos_protegidos SMALLINT NOT NULL,
    ventana_liberacion_h SMALLINT NOT NULL,
    bloqueo_temporal_min SMALLINT NOT NULL,
    n_ventana_estimacion SMALLINT NOT NULL,
    anticipacion_confirm_h SMALLINT NOT NULL,
    vigencia_indicada_dias SMALLINT NOT NULL,
    mostrar_penalizacion BOOLEAN NOT NULL,
    texto_penalizacion VARCHAR(255),
    max_intentos_login SMALLINT NOT NULL,
    minutos_bloqueo_login SMALLINT NOT NULL,
    whatsapp_numero VARCHAR(20),
    dias_recordar_sugerencia SMALLINT NOT NULL,
    id_tipo_web_defecto SMALLINT NOT NULL,
    CONSTRAINT pk_parametro_consultorio PRIMARY KEY (id_parametro),
    CONSTRAINT chk_param_fila_unica CHECK (id_parametro = 1),
    CONSTRAINT chk_param_incremento CHECK (incremento_min > 0 AND incremento_min % 15 = 0),
    CONSTRAINT chk_param_politica CHECK (politica_proteccion IN ('NUNCA','BLANDA','DURA')),
    CONSTRAINT chk_param_n_ventana CHECK (n_ventana_estimacion > 0),
    CONSTRAINT chk_param_penalizacion CHECK (NOT mostrar_penalizacion OR texto_penalizacion IS NOT NULL),
    CONSTRAINT chk_param_rangos CHECK (minutos_protegidos >= 0 AND ventana_liberacion_h >= 0 AND bloqueo_temporal_min > 0 AND anticipacion_confirm_h >= 0 AND vigencia_indicada_dias > 0 AND max_intentos_login > 0 AND minutos_bloqueo_login > 0 AND dias_recordar_sugerencia > 0)
);

-- PLAZO_TRANSICION
CREATE TABLE plazo_transicion (
    transicion VARCHAR(25) NOT NULL,
    minutos_desde SMALLINT,
    minutos_hasta SMALLINT,
    hasta_fin_dia SMALLINT,
    CONSTRAINT pk_plazo_transicion PRIMARY KEY (transicion),
    CONSTRAINT chk_plazo_transicion CHECK (transicion IN ('NO_ASISTIO','RETIRO','CANCELADA_POR_PACIENTE','CANCELADA_POR_CLINICA','CONFIRMADA','ASISTIO','REALIZADA')),
    CONSTRAINT chk_plazo_orden CHECK (minutos_desde IS NULL OR minutos_hasta IS NULL OR minutos_hasta >= minutos_desde),
    CONSTRAINT chk_plazo_fin_dia CHECK (hasta_fin_dia IS NULL OR (hasta_fin_dia >= 0 AND minutos_hasta IS NULL))
);

-- AVISO: Contenido de la sección Avisos del paciente.
CREATE TABLE aviso (
    id_aviso BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    banner_url VARCHAR(255) NOT NULL,
    titulo VARCHAR(100) NOT NULL,
    texto VARCHAR(300) NOT NULL,
    enlace VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL,
    CONSTRAINT pk_aviso PRIMARY KEY (id_aviso)
);

-- ESPECIALIDAD: Catálogo de especialidades.
CREATE TABLE especialidad (
    id_especialidad SMALLINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    activo BOOLEAN NOT NULL,
    CONSTRAINT pk_especialidad PRIMARY KEY (id_especialidad)
);

-- TIPO_CONSULTA: Catálogo configurable de tipos de consulta.
CREATE TABLE tipo_consulta (
    id_tipo_consulta SMALLINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    nombre VARCHAR(60) NOT NULL UNIQUE,
    duracion_defecto SMALLINT NOT NULL,
    incremento_min SMALLINT,
    ajuste_medico BOOLEAN NOT NULL,
    ajuste_recepcion BOOLEAN NOT NULL,
    reservable_web BOOLEAN NOT NULL,
    modalidad_permitida VARCHAR(12) NOT NULL,
    activo BOOLEAN NOT NULL,
    CONSTRAINT pk_tipo_consulta PRIMARY KEY (id_tipo_consulta),
    CONSTRAINT chk_tc_duracion CHECK (duracion_defecto > 0 AND duracion_defecto % 15 = 0),
    CONSTRAINT chk_tc_incremento CHECK (incremento_min IS NULL OR (incremento_min > 0 AND incremento_min % 15 = 0)),
    CONSTRAINT chk_tc_modalidad CHECK (modalidad_permitida IN ('PRESENCIAL','TELECONSULTA','AMBAS'))
);

-- MEDICO_ESPECIALIDAD: Especialidades de cada médico.
CREATE TABLE medico_especialidad (
    id_medico BIGINT NOT NULL,
    id_especialidad SMALLINT NOT NULL,
    CONSTRAINT pk_medico_especialidad PRIMARY KEY (id_medico, id_especialidad)
);

-- MEDICO_TIPO_CONSULTA: Duración que usa cada médico por tipo de consulta.
CREATE TABLE medico_tipo_consulta (
    id_medico BIGINT NOT NULL,
    id_tipo_consulta SMALLINT NOT NULL,
    duracion_configurada SMALLINT NOT NULL,
    sugerencia_rechazada SMALLINT,
    recordar_desde TIMESTAMP,
    CONSTRAINT pk_medico_tipo_consulta PRIMARY KEY (id_medico, id_tipo_consulta),
    CONSTRAINT chk_mtc_duracion CHECK (duracion_configurada > 0 AND duracion_configurada % 15 = 0),
    CONSTRAINT chk_mtc_sugerencia CHECK (sugerencia_rechazada IS NULL OR (sugerencia_rechazada > 0 AND sugerencia_rechazada % 15 = 0))
);

-- HORARIO_VERSION: Versión estructural del horario con vigencia.
CREATE TABLE horario_version (
    id_horario_version BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_medico BIGINT NOT NULL,
    vigente_desde DATE NOT NULL,
    vigente_hasta DATE,
    CONSTRAINT pk_horario_version PRIMARY KEY (id_horario_version),
    CONSTRAINT chk_hv_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    CONSTRAINT excl_hv_vigencia EXCLUDE USING gist (id_medico WITH =, daterange(vigente_desde, vigente_hasta, '[]') WITH &&)
);

-- FRANJA_HORARIA: Lapsos de atención de una versión.
CREATE TABLE franja_horaria (
    id_franja BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_horario_version BIGINT NOT NULL,
    dia_semana SMALLINT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    CONSTRAINT pk_franja_horaria PRIMARY KEY (id_franja),
    CONSTRAINT chk_franja_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_franja_rango CHECK (hora_fin > hora_inicio),
    CONSTRAINT chk_franja_rejilla CHECK (EXTRACT(MINUTE FROM hora_inicio)::int % 15 = 0 AND EXTRACT(SECOND FROM hora_inicio) = 0 AND EXTRACT(MINUTE FROM hora_fin)::int % 15 = 0 AND EXTRACT(SECOND FROM hora_fin) = 0)
);

-- EXCEPCION_HORARIO: Bloqueo puntual sobre fechas concretas.
CREATE TABLE excepcion_horario (
    id_excepcion BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_medico BIGINT NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    hora_inicio TIME,
    hora_fin TIME,
    motivo VARCHAR(255) NOT NULL,
    CONSTRAINT pk_excepcion_horario PRIMARY KEY (id_excepcion),
    CONSTRAINT chk_exc_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT chk_exc_horas CHECK ((hora_inicio IS NULL AND hora_fin IS NULL) OR (hora_inicio IS NOT NULL AND hora_fin IS NOT NULL AND hora_fin > hora_inicio)),
    CONSTRAINT chk_exc_rejilla CHECK (hora_inicio IS NULL OR (EXTRACT(MINUTE FROM hora_inicio)::int % 15 = 0 AND EXTRACT(SECOND FROM hora_inicio) = 0 AND EXTRACT(MINUTE FROM hora_fin)::int % 15 = 0 AND EXTRACT(SECOND FROM hora_fin) = 0))
);

-- BLOQUEO_TEMPORAL: Reserva provisional mientras el usuario completa el formulario.
CREATE TABLE bloqueo_temporal (
    id_bloqueo BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_medico BIGINT NOT NULL,
    id_cuenta BIGINT NOT NULL,
    inicio TIMESTAMP NOT NULL,
    fin TIMESTAMP NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    CONSTRAINT pk_bloqueo_temporal PRIMARY KEY (id_bloqueo),
    CONSTRAINT chk_bloq_rango CHECK (fin > inicio),
    CONSTRAINT chk_bloq_rejilla CHECK (EXTRACT(MINUTE FROM inicio)::int % 15 = 0 AND EXTRACT(SECOND FROM inicio) = 0 AND EXTRACT(MINUTE FROM fin)::int % 15 = 0 AND EXTRACT(SECOND FROM fin) = 0)
);

-- CITA: Reserva. Conserva su identificador al reprogramarse.
CREATE TABLE cita (
    id_cita BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_paciente BIGINT NOT NULL,
    id_cuenta_titular BIGINT,
    id_medico BIGINT NOT NULL,
    id_especialidad SMALLINT NOT NULL,
    id_tipo_consulta SMALLINT NOT NULL,
    inicio TIMESTAMP NOT NULL,
    fin TIMESTAMP NOT NULL,
    estado VARCHAR(25) NOT NULL,
    modalidad VARCHAR(12) NOT NULL,
    canal_origen VARCHAR(15) NOT NULL,
    es_primera_vez BOOLEAN NOT NULL,
    hora_llegada TIMESTAMP,
    inicio_real TIMESTAMP,
    fin_real TIMESTAMP,
    indicaciones TEXT,
    indicaciones_admin TEXT,
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT pk_cita PRIMARY KEY (id_cita),
    CONSTRAINT chk_cita_rango CHECK (fin > inicio),
    CONSTRAINT chk_cita_rejilla CHECK (EXTRACT(MINUTE FROM inicio)::int % 15 = 0 AND EXTRACT(SECOND FROM inicio) = 0 AND EXTRACT(MINUTE FROM fin)::int % 15 = 0 AND EXTRACT(SECOND FROM fin) = 0),
    CONSTRAINT chk_cita_estado CHECK (estado IN ('PROGRAMADA','CONFIRMADA','ASISTIO','REALIZADA','NO_ASISTIO','CANCELADA_POR_PACIENTE','CANCELADA_POR_CLINICA')),
    CONSTRAINT chk_cita_modalidad CHECK (modalidad IN ('PRESENCIAL','TELECONSULTA')),
    CONSTRAINT chk_cita_canal CHECK (canal_origen IN ('WEB','PRESENCIAL','TELEFONO','WHATSAPP','DERIVACION','CONSULTA_MEDICA')),
    CONSTRAINT chk_cita_atencion_real CHECK (inicio_real IS NULL OR fin_real IS NULL OR fin_real > inicio_real),
    CONSTRAINT chk_cita_marcas CHECK ((estado NOT IN ('ASISTIO','REALIZADA') OR hora_llegada IS NOT NULL) AND (estado <> 'REALIZADA' OR (inicio_real IS NOT NULL AND fin_real IS NOT NULL))),
    CONSTRAINT excl_cita_solape EXCLUDE USING gist (id_medico WITH =, tsrange(inicio, fin) WITH &&) WHERE (estado NOT IN ('CANCELADA_POR_PACIENTE','CANCELADA_POR_CLINICA'))
);

-- CITA_EVENTO: Historial de la cita. Sin UPDATE ni DELETE.
CREATE TABLE cita_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cita BIGINT NOT NULL,
    id_cuenta BIGINT,
    id_llamada BIGINT,
    tipo_evento VARCHAR(20) NOT NULL,
    estado_anterior VARCHAR(25),
    estado_nuevo VARCHAR(25),
    inicio_anterior TIMESTAMP,
    fin_anterior TIMESTAMP,
    inicio_nuevo TIMESTAMP,
    fin_nuevo TIMESTAMP,
    motivo VARCHAR(255),
    fecha_hora TIMESTAMP NOT NULL,
    CONSTRAINT pk_cita_evento PRIMARY KEY (id_evento),
    CONSTRAINT chk_evento_tipo CHECK (tipo_evento IN ('CREACION','CAMBIO_ESTADO','REPROGRAMACION','AJUSTE_DURACION','LLEGADA','INICIO_ATENCION','FIN_ATENCION')),
    CONSTRAINT chk_evento_confirmacion CHECK (estado_nuevo IS NULL OR estado_nuevo <> 'CONFIRMADA' OR id_llamada IS NOT NULL),
    CONSTRAINT chk_evento_retiro CHECK (NOT (estado_anterior = 'ASISTIO' AND estado_nuevo = 'NO_ASISTIO' AND motivo IS NULL)),
    CONSTRAINT chk_evento_estados CHECK ((estado_anterior IS NULL OR estado_anterior IN ('PROGRAMADA','CONFIRMADA','ASISTIO','REALIZADA','NO_ASISTIO','CANCELADA_POR_PACIENTE','CANCELADA_POR_CLINICA')) AND (estado_nuevo IS NULL OR estado_nuevo IN ('PROGRAMADA','CONFIRMADA','ASISTIO','REALIZADA','NO_ASISTIO','CANCELADA_POR_PACIENTE','CANCELADA_POR_CLINICA'))),
    CONSTRAINT chk_evento_cancel_clinica CHECK (estado_nuevo IS NULL OR estado_nuevo <> 'CANCELADA_POR_CLINICA' OR motivo IS NOT NULL)
);

-- CITA_INDICADA: Siguiente cita prescrita por el médico.
CREATE TABLE cita_indicada (
    id_indicacion BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cita_origen BIGINT NOT NULL,
    id_tipo_consulta SMALLINT NOT NULL,
    duracion SMALLINT NOT NULL,
    fecha_recomendada DATE NOT NULL,
    vigente_hasta DATE NOT NULL,
    indicaciones TEXT,
    estado VARCHAR(12) NOT NULL,
    id_cita_generada BIGINT UNIQUE,
    motivo_descarte VARCHAR(255),
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT pk_cita_indicada PRIMARY KEY (id_indicacion),
    CONSTRAINT chk_ci_duracion CHECK (duracion > 0 AND duracion % 15 = 0),
    CONSTRAINT chk_ci_vigencia CHECK (vigente_hasta >= fecha_recomendada),
    CONSTRAINT chk_ci_estado CHECK (estado IN ('PENDIENTE','AGENDADA','VENCIDA','DESCARTADA')),
    CONSTRAINT chk_ci_generada CHECK ((estado = 'AGENDADA' AND id_cita_generada IS NOT NULL) OR (estado <> 'AGENDADA' AND id_cita_generada IS NULL)),
    CONSTRAINT chk_ci_descarte CHECK (estado <> 'DESCARTADA' OR motivo_descarte IS NOT NULL)
);

-- PREFERENCIA_DISPONIBILIDAD: Consentimiento y condiciones para adelantar una cita.
CREATE TABLE preferencia_disponibilidad (
    id_preferencia BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cita BIGINT NOT NULL UNIQUE,
    alcance VARCHAR(12) NOT NULL,
    min_llegada_min SMALLINT NOT NULL,
    dias_previos SMALLINT NOT NULL,
    inscrito_en TIMESTAMP NOT NULL,
    consentido_en TIMESTAMP NOT NULL,
    revocado_en TIMESTAMP,
    CONSTRAINT pk_preferencia_disponibilidad PRIMARY KEY (id_preferencia),
    CONSTRAINT chk_pref_alcance CHECK (alcance IN ('MEDICO','ESPECIALIDAD')),
    CONSTRAINT chk_pref_revocacion CHECK (revocado_en IS NULL OR revocado_en >= consentido_en),
    CONSTRAINT chk_pref_rangos CHECK (min_llegada_min >= 0 AND dias_previos >= 0),
    CONSTRAINT chk_pref_inscripcion CHECK (consentido_en >= inscrito_en)
);

-- OPORTUNIDAD: Bloque liberado. Hecho histórico, no un slot.
CREATE TABLE oportunidad (
    id_oportunidad BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cita_origen BIGINT NOT NULL,
    id_medico BIGINT NOT NULL,
    id_oportunidad_padre BIGINT,
    inicio TIMESTAMP NOT NULL,
    fin TIMESTAMP NOT NULL,
    estado VARCHAR(12) NOT NULL,
    creada_en TIMESTAMP NOT NULL,
    cerrada_en TIMESTAMP,
    id_cita_cobertura BIGINT,
    CONSTRAINT pk_oportunidad PRIMARY KEY (id_oportunidad),
    CONSTRAINT chk_op_rango CHECK (fin > inicio),
    CONSTRAINT chk_op_rejilla CHECK (EXTRACT(MINUTE FROM inicio)::int % 15 = 0 AND EXTRACT(SECOND FROM inicio) = 0 AND EXTRACT(MINUTE FROM fin)::int % 15 = 0 AND EXTRACT(SECOND FROM fin) = 0),
    CONSTRAINT chk_op_estado CHECK (estado IN ('ABIERTA','CUBIERTA','EXPIRADA','REEMPLAZADA')),
    CONSTRAINT chk_op_cierre CHECK ((estado = 'ABIERTA' AND cerrada_en IS NULL) OR (estado <> 'ABIERTA' AND cerrada_en IS NOT NULL)),
    CONSTRAINT chk_op_cobertura CHECK ((estado = 'CUBIERTA') = (id_cita_cobertura IS NOT NULL))
);

-- REGISTRO_LLAMADA: Resultado de cada llamada de las tres colas. Sin UPDATE ni DELETE. El número llamado es el contacto de la cita (cuenta titular o, sin ella, el paciente).
CREATE TABLE registro_llamada (
    id_llamada BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    id_cuenta_operador BIGINT NOT NULL,
    tipo_cola VARCHAR(15) NOT NULL,
    id_cita BIGINT,
    id_oportunidad BIGINT,
    id_indicacion BIGINT,
    posicion_lista SMALLINT,
    telefono_llamado VARCHAR(20) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    observacion VARCHAR(255),
    fecha_hora TIMESTAMP NOT NULL,
    CONSTRAINT pk_registro_llamada PRIMARY KEY (id_llamada),
    CONSTRAINT chk_rl_cola CHECK (tipo_cola IN ('CONFIRMACION','CUPO_LIBERADO','CITA_PENDIENTE')),
    CONSTRAINT chk_rl_referencias CHECK ((tipo_cola = 'CONFIRMACION' AND id_cita IS NOT NULL AND id_oportunidad IS NULL AND id_indicacion IS NULL AND posicion_lista IS NULL) OR (tipo_cola = 'CUPO_LIBERADO' AND id_oportunidad IS NOT NULL AND id_cita IS NOT NULL AND posicion_lista IS NOT NULL AND id_indicacion IS NULL) OR (tipo_cola = 'CITA_PENDIENTE' AND id_indicacion IS NOT NULL AND id_cita IS NULL AND id_oportunidad IS NULL AND posicion_lista IS NULL)),
    CONSTRAINT chk_rl_resultado CHECK ((tipo_cola = 'CONFIRMACION' AND resultado IN ('CONFIRMO','NO_CONTESTO','CANCELO','REPROGRAMO','NUMERO_INVALIDO')) OR (tipo_cola = 'CUPO_LIBERADO' AND resultado IN ('ACEPTO','RECHAZO','NO_CONTESTO','NO_PUEDE_LLEGAR','NUMERO_INVALIDO','OTRO')) OR (tipo_cola = 'CITA_PENDIENTE' AND resultado IN ('AGENDO','NO_CONTESTO','NO_LE_INTERESA','NUMERO_INVALIDO'))),
    CONSTRAINT chk_rl_posicion CHECK (posicion_lista IS NULL OR posicion_lista > 0)
);

-- CANDIDATO_POSTERGADO
CREATE TABLE candidato_postergado (
    id_oportunidad BIGINT NOT NULL,
    id_cita BIGINT NOT NULL,
    id_cuenta_operador BIGINT NOT NULL,
    postergado_en TIMESTAMP NOT NULL,
    CONSTRAINT pk_candidato_postergado PRIMARY KEY (id_oportunidad, id_cita)
);

-- Claves foráneas (al final, para que el orden de creación de las tablas no importe).
ALTER TABLE cuenta ADD CONSTRAINT fk_cuenta_id_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol);
ALTER TABLE cuenta ADD CONSTRAINT fk_cuenta_id_paciente_propio FOREIGN KEY (id_paciente_propio) REFERENCES paciente (id_paciente);
ALTER TABLE cuenta_paciente ADD CONSTRAINT fk_cuenta_paciente_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE cuenta_paciente ADD CONSTRAINT fk_cuenta_paciente_id_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente);
ALTER TABLE medico ADD CONSTRAINT fk_medico_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE token_recuperacion ADD CONSTRAINT fk_token_recuperacion_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE auditoria ADD CONSTRAINT fk_auditoria_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE medico_especialidad ADD CONSTRAINT fk_medico_especialidad_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE medico_especialidad ADD CONSTRAINT fk_medico_especialidad_id_especialidad FOREIGN KEY (id_especialidad) REFERENCES especialidad (id_especialidad);
ALTER TABLE medico_tipo_consulta ADD CONSTRAINT fk_medico_tipo_consulta_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE medico_tipo_consulta ADD CONSTRAINT fk_medico_tipo_consulta_id_tipo_consulta FOREIGN KEY (id_tipo_consulta) REFERENCES tipo_consulta (id_tipo_consulta);
ALTER TABLE horario_version ADD CONSTRAINT fk_horario_version_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE franja_horaria ADD CONSTRAINT fk_franja_horaria_id_horario_version FOREIGN KEY (id_horario_version) REFERENCES horario_version (id_horario_version);
ALTER TABLE excepcion_horario ADD CONSTRAINT fk_excepcion_horario_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE bloqueo_temporal ADD CONSTRAINT fk_bloqueo_temporal_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE bloqueo_temporal ADD CONSTRAINT fk_bloqueo_temporal_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE cita ADD CONSTRAINT fk_cita_id_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente);
ALTER TABLE cita ADD CONSTRAINT fk_cita_id_cuenta_titular FOREIGN KEY (id_cuenta_titular) REFERENCES cuenta (id_cuenta);
ALTER TABLE cita ADD CONSTRAINT fk_cita_id_medico_id_especialidad FOREIGN KEY (id_medico, id_especialidad) REFERENCES medico_especialidad (id_medico, id_especialidad);
ALTER TABLE cita ADD CONSTRAINT fk_cita_id_tipo_consulta FOREIGN KEY (id_tipo_consulta) REFERENCES tipo_consulta (id_tipo_consulta);
ALTER TABLE cita_evento ADD CONSTRAINT fk_cita_evento_id_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita);
ALTER TABLE cita_evento ADD CONSTRAINT fk_cita_evento_id_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuenta (id_cuenta);
ALTER TABLE cita_evento ADD CONSTRAINT fk_cita_evento_id_llamada FOREIGN KEY (id_llamada) REFERENCES registro_llamada (id_llamada);
ALTER TABLE cita_indicada ADD CONSTRAINT fk_cita_indicada_id_cita_origen FOREIGN KEY (id_cita_origen) REFERENCES cita (id_cita);
ALTER TABLE cita_indicada ADD CONSTRAINT fk_cita_indicada_id_cita_generada FOREIGN KEY (id_cita_generada) REFERENCES cita (id_cita);
ALTER TABLE cita_indicada ADD CONSTRAINT fk_cita_indicada_id_tipo_consulta FOREIGN KEY (id_tipo_consulta) REFERENCES tipo_consulta (id_tipo_consulta);
ALTER TABLE preferencia_disponibilidad ADD CONSTRAINT fk_preferencia_disponibilidad_id_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita);
ALTER TABLE oportunidad ADD CONSTRAINT fk_oportunidad_id_medico FOREIGN KEY (id_medico) REFERENCES medico (id_medico);
ALTER TABLE oportunidad ADD CONSTRAINT fk_oportunidad_id_cita_origen FOREIGN KEY (id_cita_origen) REFERENCES cita (id_cita);
ALTER TABLE oportunidad ADD CONSTRAINT fk_oportunidad_id_oportunidad_padre FOREIGN KEY (id_oportunidad_padre) REFERENCES oportunidad (id_oportunidad);
ALTER TABLE registro_llamada ADD CONSTRAINT fk_registro_llamada_id_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita);
ALTER TABLE registro_llamada ADD CONSTRAINT fk_registro_llamada_id_oportunidad FOREIGN KEY (id_oportunidad) REFERENCES oportunidad (id_oportunidad);
ALTER TABLE registro_llamada ADD CONSTRAINT fk_registro_llamada_id_indicacion FOREIGN KEY (id_indicacion) REFERENCES cita_indicada (id_indicacion);
ALTER TABLE registro_llamada ADD CONSTRAINT fk_registro_llamada_id_cuenta_operador FOREIGN KEY (id_cuenta_operador) REFERENCES cuenta (id_cuenta);
ALTER TABLE candidato_postergado ADD CONSTRAINT fk_candidato_postergado_id_oportunidad FOREIGN KEY (id_oportunidad) REFERENCES oportunidad (id_oportunidad);
ALTER TABLE candidato_postergado ADD CONSTRAINT fk_candidato_postergado_id_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita);
ALTER TABLE parametro_consultorio ADD CONSTRAINT fk_parametro_consultorio_id_tipo_web_defecto FOREIGN KEY (id_tipo_web_defecto) REFERENCES tipo_consulta (id_tipo_consulta);
ALTER TABLE oportunidad ADD CONSTRAINT fk_oportunidad_id_cita_cobertura FOREIGN KEY (id_cita_cobertura) REFERENCES cita (id_cita);
ALTER TABLE candidato_postergado ADD CONSTRAINT fk_candidato_postergado_id_cuenta_operador FOREIGN KEY (id_cuenta_operador) REFERENCES cuenta (id_cuenta);

-- Descripciones, visibles con \d+ en psql y en herramientas gráficas.
COMMENT ON COLUMN rol.id_rol IS 'Identificador.';
COMMENT ON COLUMN rol.nombre IS 'PACIENTE, MEDICO, RECEPCION, ADMINISTRADOR.';
COMMENT ON TABLE rol IS 'Roles del sistema.';
COMMENT ON COLUMN cuenta.id_cuenta IS 'Identificador.';
COMMENT ON COLUMN cuenta.correo IS 'Usuario de inicio de sesión.';
COMMENT ON COLUMN cuenta.nombre_visible IS 'Nombre del personal de recepción y administración, para mostrar quién llamó o quién hizo un cambio. Lo exige el servicio en esos roles; NULL en pacientes (su nombre sale del paciente propio) y médicos (sale de MEDICO).';
COMMENT ON COLUMN cuenta.password_hash IS 'Hash (bcrypt o Argon2), nunca la contraseña.';
COMMENT ON COLUMN cuenta.id_rol IS 'Rol de la cuenta.';
COMMENT ON COLUMN cuenta.id_paciente_propio IS 'Paciente del titular, creado al registrarse. Obligatorio en cuentas con rol PACIENTE (lo valida el servicio: un CHECK no puede leer el rol). NULL en cuentas de personal. Su teléfono es el contacto de la cuenta.';
COMMENT ON COLUMN cuenta.estado IS 'ACTIVA, BLOQUEADA (bloqueo manual del administrador, sin fecha de fin) o BAJA. El bloqueo temporal por intentos fallidos no usa este campo: es bloqueado_hasta.';
COMMENT ON COLUMN cuenta.intentos_fallidos IS 'Se reinicia al iniciar sesión correctamente.';
COMMENT ON COLUMN cuenta.creado_en IS 'Fecha de alta.';
COMMENT ON TABLE cuenta IS 'Credenciales y estado de acceso.';
COMMENT ON COLUMN paciente.id_paciente IS 'Identificador.';
COMMENT ON COLUMN paciente.tipo_documento IS 'DNI, CE, PASAPORTE.';
COMMENT ON COLUMN paciente.nro_documento IS 'Único junto con el tipo de documento.';
COMMENT ON COLUMN paciente.nombres IS 'Nombres del paciente, como figuran en su documento.';
COMMENT ON COLUMN paciente.apellidos IS 'Apellidos del paciente.';
COMMENT ON COLUMN paciente.correo IS 'Opcional para pacientes registrados por recepción.';
COMMENT ON TABLE paciente IS 'Persona atendida.';
COMMENT ON COLUMN cuenta_paciente.id_cuenta IS 'Cuenta que agenda.';
COMMENT ON COLUMN cuenta_paciente.id_paciente IS 'Paciente tercero.';
COMMENT ON COLUMN cuenta_paciente.consentimiento_en IS 'Fecha de la declaración de consentimiento.';
COMMENT ON COLUMN cuenta_paciente.revocado_en IS 'Fecha en que el paciente retiró su consentimiento, registrada por recepción. Desde entonces la cuenta no puede agendarle citas nuevas; sus citas ya agendadas se mantienen. NULL = vigente.';
COMMENT ON TABLE cuenta_paciente IS 'Pacientes terceros para los que una cuenta agenda, con consentimiento. La cuenta solo ve las citas que ella agendó.';
COMMENT ON COLUMN medico.id_medico IS 'Identificador.';
COMMENT ON COLUMN medico.id_cuenta IS 'Cuenta de acceso del médico.';
COMMENT ON COLUMN medico.nombres IS 'Nombres del médico, como se muestran al paciente en la búsqueda y en sus citas.';
COMMENT ON COLUMN medico.apellidos IS 'Apellidos del médico.';
COMMENT ON COLUMN medico.activo IS 'El médico recibe citas nuevas y aparece en las búsquedas. Un médico inactivo puede seguir entrando con su cuenta para ver su historial; el acceso lo controla CUENTA.estado.';
COMMENT ON TABLE medico IS 'Profesional que atiende.';
COMMENT ON COLUMN token_recuperacion.jti IS 'Identificador único del token firmado.';
COMMENT ON COLUMN token_recuperacion.id_cuenta IS 'Cuenta que pidió recuperar su contraseña.';
COMMENT ON COLUMN token_recuperacion.emitido_en IS 'Cuándo se generó el enlace.';
COMMENT ON COLUMN token_recuperacion.expira_en IS 'Cuándo caduca el enlace; después se ofrece pedir uno nuevo.';
COMMENT ON COLUMN token_recuperacion.consumido_en IS 'Marca el único uso permitido.';
COMMENT ON COLUMN token_recuperacion.revocado IS 'Se activa al cambiar contraseña o dar de baja la cuenta.';
COMMENT ON TABLE token_recuperacion IS 'Control de un solo uso del token de recuperación.';
COMMENT ON COLUMN auditoria.id_auditoria IS 'Identificador del registro de auditoría.';
COMMENT ON COLUMN auditoria.id_cuenta IS 'NULL para acciones automáticas del sistema.';
COMMENT ON COLUMN auditoria.accion IS 'Qué ocurrió. Valores previstos: LOGIN_OK, LOGIN_FALLIDO, CUENTA_BLOQUEADA, CONFIG_CAMBIO, AUTORIZACION_VERBAL, PREFERENCIA_CAMBIO, CAMBIO_HORARIO_RECHAZADO, VINCULO_PACIENTE.';
COMMENT ON COLUMN auditoria.entidad IS 'Tabla afectada.';
COMMENT ON COLUMN auditoria.id_registro IS 'Clave de la fila afectada como texto (admite claves compuestas, p. ej. "12|3", y de texto, p. ej. "RETIRO"). NULL cuando no hay fila, p. ej. un inicio de sesión con un correo inexistente.';
COMMENT ON COLUMN auditoria.detalle IS 'Valores anterior y nuevo.';
COMMENT ON COLUMN auditoria.motivo IS 'Motivo declarado cuando la acción lo requiere (p. ej. un cambio de configuración o un desbloqueo).';
COMMENT ON COLUMN auditoria.fecha_hora IS 'Cuándo ocurrió la acción.';
COMMENT ON TABLE auditoria IS 'Bitácora persistente. Sin UPDATE ni DELETE.';
COMMENT ON COLUMN parametro_consultorio.id_parametro IS 'Siempre 1 (CHECK).';
COMMENT ON COLUMN parametro_consultorio.incremento_min IS 'Rejilla temporal. Sugerido: 15.';
COMMENT ON COLUMN parametro_consultorio.politica_proteccion IS 'NUNCA, BLANDA, DURA.';
COMMENT ON COLUMN parametro_consultorio.minutos_protegidos IS 'Minutos por día protegidos para consultas largas.';
COMMENT ON COLUMN parametro_consultorio.ventana_liberacion_h IS 'Horas antes de la cita en que caduca la protección blanda.';
COMMENT ON COLUMN parametro_consultorio.bloqueo_temporal_min IS 'Duración del bloqueo de un horario en selección.';
COMMENT ON COLUMN parametro_consultorio.n_ventana_estimacion IS 'Citas de la ventana móvil. Sugerido: 30.';
COMMENT ON COLUMN parametro_consultorio.anticipacion_confirm_h IS 'Horas antes en que una cita entra a la cola de confirmación.';
COMMENT ON COLUMN parametro_consultorio.vigencia_indicada_dias IS 'Vigencia por defecto de una cita indicada.';
COMMENT ON COLUMN parametro_consultorio.mostrar_penalizacion IS 'Si la ventana de cancelación del paciente muestra un aviso de penalización por cancelación tardía.';
COMMENT ON COLUMN parametro_consultorio.texto_penalizacion IS 'Texto de ese aviso. Obligatorio si mostrar_penalizacion es verdadero.';
COMMENT ON COLUMN parametro_consultorio.minutos_bloqueo_login IS 'Duración de ese bloqueo temporal, en minutos.';
COMMENT ON COLUMN parametro_consultorio.id_tipo_web_defecto IS 'Tipo de consulta con el que continúa [Agendar una consulta nueva] en el portal. Debe ser reservable por web (lo valida el servicio).';
COMMENT ON TABLE parametro_consultorio IS 'Parámetros globales. Una sola fila.';
COMMENT ON COLUMN plazo_transicion.minutos_desde IS 'Desde cuándo se permite, en minutos relativos a la hora de inicio de la cita; negativo = antes. NULL = sin límite inferior. Configurable por el administrador.';
COMMENT ON COLUMN plazo_transicion.minutos_hasta IS 'Hasta cuándo se permite, en minutos relativos a la hora de inicio. NULL = sin límite, o el que fije hasta_fin_dia. Configurable por el administrador.';
COMMENT ON COLUMN plazo_transicion.hasta_fin_dia IS 'Límite fijo por calendario: 0 = fin del día de la cita, 1 = fin del día siguiente. Regla del sistema, no editable. Excluye minutos_hasta.';
COMMENT ON COLUMN aviso.id_aviso IS 'Identificador del aviso.';
COMMENT ON COLUMN aviso.banner_url IS 'Imagen de cabecera de la tarjeta del aviso.';
COMMENT ON COLUMN aviso.titulo IS 'Título del aviso.';
COMMENT ON COLUMN aviso.texto IS 'Límite de 300 caracteres del flujo.';
COMMENT ON COLUMN aviso.enlace IS 'Página que abre [Conocer más], en otra pestaña.';
COMMENT ON COLUMN aviso.activo IS 'Si el aviso se muestra en la sección Avisos del paciente.';
COMMENT ON TABLE aviso IS 'Contenido de la sección Avisos del paciente.';
COMMENT ON COLUMN especialidad.id_especialidad IS 'Identificador de la especialidad.';
COMMENT ON COLUMN especialidad.nombre IS 'Nombre de la especialidad (p. ej. Cardiología). Único.';
COMMENT ON COLUMN especialidad.descripcion IS 'Descripción breve para el paciente.';
COMMENT ON COLUMN especialidad.activo IS 'Si la especialidad se ofrece para agendar citas nuevas.';
COMMENT ON TABLE especialidad IS 'Catálogo de especialidades.';
COMMENT ON COLUMN tipo_consulta.id_tipo_consulta IS 'Identificador del tipo de consulta.';
COMMENT ON COLUMN tipo_consulta.nombre IS 'Nombre del tipo (p. ej. Consulta nueva, Control, Procedimiento). Único.';
COMMENT ON COLUMN tipo_consulta.duracion_defecto IS 'Múltiplo del incremento.';
COMMENT ON COLUMN tipo_consulta.incremento_min IS 'NULL = hereda el global.';
COMMENT ON COLUMN tipo_consulta.ajuste_medico IS 'El médico puede ajustar la duración.';
COMMENT ON COLUMN tipo_consulta.ajuste_recepcion IS 'Recepción puede ajustar la duración.';
COMMENT ON COLUMN tipo_consulta.reservable_web IS 'Visible en el portal del paciente.';
COMMENT ON COLUMN tipo_consulta.modalidad_permitida IS 'PRESENCIAL, TELECONSULTA, AMBAS.';
COMMENT ON COLUMN tipo_consulta.activo IS 'Si el tipo se puede usar en citas nuevas.';
COMMENT ON TABLE tipo_consulta IS 'Catálogo configurable de tipos de consulta.';
COMMENT ON COLUMN medico_especialidad.id_medico IS 'Médico.';
COMMENT ON COLUMN medico_especialidad.id_especialidad IS 'Especialidad que ejerce. Un médico puede tener varias.';
COMMENT ON TABLE medico_especialidad IS 'Especialidades de cada médico.';
COMMENT ON COLUMN medico_tipo_consulta.id_medico IS 'Médico.';
COMMENT ON COLUMN medico_tipo_consulta.duracion_configurada IS 'La que usa el sistema para agendar.';
COMMENT ON COLUMN medico_tipo_consulta.sugerencia_rechazada IS 'Último valor sugerido y rechazado; no se repite mientras la mediana no cambie de ranura.';
COMMENT ON COLUMN medico_tipo_consulta.recordar_desde IS 'Con «Recordar después», la sugerencia no vuelve a mostrarse antes de esta fecha (ahora + dias_recordar_sugerencia). NULL = sin posponer.';
COMMENT ON TABLE medico_tipo_consulta IS 'Duración que usa cada médico por tipo de consulta.';
COMMENT ON COLUMN horario_version.id_horario_version IS 'Identificador de la versión de horario.';
COMMENT ON COLUMN horario_version.id_medico IS 'Médico al que pertenece el horario.';
COMMENT ON COLUMN horario_version.vigente_desde IS 'Desde qué fecha rige esta versión.';
COMMENT ON COLUMN horario_version.vigente_hasta IS 'NULL = versión vigente.';
COMMENT ON TABLE horario_version IS 'Versión estructural del horario con vigencia.';
COMMENT ON COLUMN franja_horaria.id_franja IS 'Identificador de la franja.';
COMMENT ON COLUMN franja_horaria.id_horario_version IS 'Versión de horario a la que pertenece. Un mismo día puede tener varias franjas (p. ej. mañana y tarde).';
COMMENT ON COLUMN franja_horaria.dia_semana IS '1 = lunes … 7 = domingo.';
COMMENT ON COLUMN franja_horaria.hora_inicio IS 'Múltiplo de 15 min.';
COMMENT ON COLUMN franja_horaria.hora_fin IS 'Múltiplo de 15 min.';
COMMENT ON TABLE franja_horaria IS 'Lapsos de atención de una versión.';
COMMENT ON COLUMN excepcion_horario.id_excepcion IS 'Identificador de la excepción.';
COMMENT ON COLUMN excepcion_horario.id_medico IS 'Médico afectado.';
COMMENT ON COLUMN excepcion_horario.fecha_inicio IS 'Primer día bloqueado.';
COMMENT ON COLUMN excepcion_horario.fecha_fin IS 'Último día bloqueado; igual a fecha_inicio si es un solo día.';
COMMENT ON COLUMN excepcion_horario.hora_inicio IS 'NULL = día completo.';
COMMENT ON COLUMN excepcion_horario.hora_fin IS 'NULL = día completo.';
COMMENT ON COLUMN excepcion_horario.motivo IS 'Razón del bloqueo (vacaciones, congreso, trámite). Obligatoria.';
COMMENT ON TABLE excepcion_horario IS 'Bloqueo puntual sobre fechas concretas.';
COMMENT ON COLUMN bloqueo_temporal.id_bloqueo IS 'Identificador del bloqueo.';
COMMENT ON COLUMN bloqueo_temporal.id_medico IS 'Médico cuyo horario se retiene.';
COMMENT ON COLUMN bloqueo_temporal.id_cuenta IS 'Quien tiene el bloqueo.';
COMMENT ON COLUMN bloqueo_temporal.inicio IS 'Inicio del horario retenido.';
COMMENT ON COLUMN bloqueo_temporal.fin IS 'Fin del horario retenido.';
COMMENT ON COLUMN bloqueo_temporal.expira_en IS 'Cuándo se libera solo si el usuario no confirma (contador visible; dura bloqueo_temporal_min).';
COMMENT ON TABLE bloqueo_temporal IS 'Reserva provisional mientras el usuario completa el formulario.';
COMMENT ON COLUMN cita.id_paciente IS 'Paciente atendido y dueño de la cita, sea cual sea el canal por el que se creó.';
COMMENT ON COLUMN cita.id_cuenta_titular IS 'Cuenta que agendó y contacto de la cita. NULL si recepción agendó sin cuenta titular; entonces el contacto es PACIENTE.telefono.';
COMMENT ON COLUMN cita.id_medico IS 'Médico que atiende.';
COMMENT ON COLUMN cita.id_especialidad IS 'Especialidad por la que se atiende.';
COMMENT ON COLUMN cita.id_tipo_consulta IS 'Tipo de consulta. Define la duración prevista y el segmento de estimación.';
COMMENT ON COLUMN cita.inicio IS 'Programado. Duración = fin − inicio.';
COMMENT ON COLUMN cita.fin IS 'Programado.';
COMMENT ON COLUMN cita.estado IS 'Estado actual. El historial está en CITA_EVENTO. ASISTIO = recepción registró la llegada; REALIZADA = el médico registró el fin de la atención.';
COMMENT ON COLUMN cita.modalidad IS 'PRESENCIAL, TELECONSULTA.';
COMMENT ON COLUMN cita.es_primera_vez IS 'Calculado por el sistema al agendar.';
COMMENT ON COLUMN cita.hora_llegada IS 'Registrada por recepción.';
COMMENT ON COLUMN cita.inicio_real IS 'Registrado por el médico.';
COMMENT ON COLUMN cita.fin_real IS 'Registrado por el médico.';
COMMENT ON COLUMN cita.indicaciones IS 'Indicaciones y observaciones del médico. Visibles para el paciente al expandir la cita.';
COMMENT ON COLUMN cita.indicaciones_admin IS 'Indicaciones administrativas de recepción (qué traer, a qué hora llegar). Visibles para el paciente junto a las del médico.';
COMMENT ON COLUMN cita.creado_en IS 'Cuándo se registró la cita.';
COMMENT ON TABLE cita IS 'Reserva. Conserva su identificador al reprogramarse.';
COMMENT ON COLUMN cita_evento.id_evento IS 'Identificador del evento.';
COMMENT ON COLUMN cita_evento.id_cita IS 'Cita a la que pertenece el evento.';
COMMENT ON COLUMN cita_evento.id_cuenta IS 'Quién actuó. NULL = acción automática.';
COMMENT ON COLUMN cita_evento.id_llamada IS 'Obligatoria si estado_nuevo = CONFIRMADA.';
COMMENT ON COLUMN cita_evento.tipo_evento IS 'CREACION, CAMBIO_ESTADO, REPROGRAMACION, AJUSTE_DURACION, LLEGADA, INICIO_ATENCION, FIN_ATENCION.';
COMMENT ON COLUMN cita_evento.estado_anterior IS 'Estado antes del evento. NULL en la creación y en eventos que no cambian el estado.';
COMMENT ON COLUMN cita_evento.estado_nuevo IS 'Estado después del evento. NULL en eventos que no cambian el estado.';
COMMENT ON COLUMN cita_evento.inicio_anterior IS 'Inicio del horario antes de una reprogramación o de un ajuste de duración.';
COMMENT ON COLUMN cita_evento.fin_anterior IS 'Fin del horario anterior.';
COMMENT ON COLUMN cita_evento.inicio_nuevo IS 'Inicio del horario nuevo.';
COMMENT ON COLUMN cita_evento.fin_nuevo IS 'Fin del horario nuevo.';
COMMENT ON COLUMN cita_evento.motivo IS 'Motivo del cambio. Obligatorio al cancelar por la clínica y en un retiro (CHECK); en las cancelaciones que registra recepción lo exige el servicio.';
COMMENT ON COLUMN cita_evento.fecha_hora IS 'Cuándo ocurrió el evento.';
COMMENT ON TABLE cita_evento IS 'Historial de la cita. Sin UPDATE ni DELETE.';
COMMENT ON COLUMN cita_indicada.id_indicacion IS 'Identificador de la indicación.';
COMMENT ON COLUMN cita_indicada.id_cita_origen IS 'Consulta en la que se indicó. De ella salen paciente y médico.';
COMMENT ON COLUMN cita_indicada.id_tipo_consulta IS 'Tipo de consulta indicado; puede ser distinto del de la cita de origen.';
COMMENT ON COLUMN cita_indicada.duracion IS 'Múltiplo del incremento.';
COMMENT ON COLUMN cita_indicada.fecha_recomendada IS 'A partir de cuándo.';
COMMENT ON COLUMN cita_indicada.vigente_hasta IS 'Hasta cuándo sigue válida.';
COMMENT ON COLUMN cita_indicada.indicaciones IS 'Indicaciones del médico para esa siguiente cita. Opcional.';
COMMENT ON COLUMN cita_indicada.estado IS 'PENDIENTE, AGENDADA, VENCIDA, DESCARTADA.';
COMMENT ON COLUMN cita_indicada.id_cita_generada IS 'Cita que la cumplió.';
COMMENT ON COLUMN cita_indicada.motivo_descarte IS 'Por qué se descartó (p. ej. «No le interesa»). Obligatorio si el estado es DESCARTADA.';
COMMENT ON COLUMN cita_indicada.creado_en IS 'Cuándo la registró el médico.';
COMMENT ON TABLE cita_indicada IS 'Siguiente cita prescrita por el médico.';
COMMENT ON COLUMN preferencia_disponibilidad.id_preferencia IS 'Identificador de la preferencia.';
COMMENT ON COLUMN preferencia_disponibilidad.id_cita IS 'Cita que el paciente quiere adelantar.';
COMMENT ON COLUMN preferencia_disponibilidad.alcance IS 'MEDICO, ESPECIALIDAD.';
COMMENT ON COLUMN preferencia_disponibilidad.min_llegada_min IS 'Tiempo mínimo que el paciente necesita para llegar, en minutos. La pantalla lo pide como valor + unidad (min u h) y lo convierte; en teleconsulta se sugiere 0.';
COMMENT ON COLUMN preferencia_disponibilidad.dias_previos IS 'Días antes de su cita en que acepta adelantar.';
COMMENT ON COLUMN preferencia_disponibilidad.consentido_en IS 'Última aceptación o edición del consentimiento. Cada cambio queda además en AUDITORIA (PREFERENCIA_CAMBIO).';
COMMENT ON COLUMN preferencia_disponibilidad.revocado_en IS 'Cuándo el paciente retiró el consentimiento, desde Mis citas o en recepción. NULL = vigente: la cita puede recibir ofertas de adelanto.';
COMMENT ON TABLE preferencia_disponibilidad IS 'Consentimiento y condiciones para adelantar una cita.';
COMMENT ON COLUMN oportunidad.id_oportunidad IS 'Identificador de la oportunidad.';
COMMENT ON COLUMN oportunidad.id_cita_origen IS 'Cita cancelada o reprogramada que liberó el horario.';
COMMENT ON COLUMN oportunidad.id_medico IS 'Médico cuya agenda liberó el bloque.';
COMMENT ON COLUMN oportunidad.id_oportunidad_padre IS 'Oportunidad anterior de la que esta sale: el remanente de un bloque cubierto en parte, o la que queda REEMPLAZADA al fusionarse con un hueco contiguo. Un solo padre.';
COMMENT ON COLUMN oportunidad.inicio IS 'Bloque coalescido al generarse.';
COMMENT ON COLUMN oportunidad.fin IS 'Fin del bloque liberado.';
COMMENT ON COLUMN oportunidad.estado IS 'ABIERTA, CUBIERTA, EXPIRADA, REEMPLAZADA.';
COMMENT ON COLUMN oportunidad.creada_en IS 'Cuándo se liberó el bloque.';
COMMENT ON COLUMN oportunidad.cerrada_en IS 'Cuándo dejó de estar ABIERTA (cubierta, expirada o reemplazada).';
COMMENT ON TABLE oportunidad IS 'Bloque liberado. Hecho histórico, no un slot.';
COMMENT ON COLUMN registro_llamada.id_llamada IS 'Identificador de la llamada.';
COMMENT ON COLUMN registro_llamada.id_cuenta_operador IS 'Quién llamó.';
COMMENT ON COLUMN registro_llamada.tipo_cola IS 'Cola de recepción de la que sale la llamada: CONFIRMACION (confirmar una cita próxima), CUPO_LIBERADO (ofrecer un bloque liberado a un candidato), CITA_PENDIENTE (agendar una cita indicada por el médico). Decide qué referencias se llenan y qué resultados valen.';
COMMENT ON COLUMN registro_llamada.id_cita IS 'Cita a confirmar, o cita principal del candidato.';
COMMENT ON COLUMN registro_llamada.id_oportunidad IS 'Solo en CUPO_LIBERADO.';
COMMENT ON COLUMN registro_llamada.id_indicacion IS 'Solo en CITA_PENDIENTE.';
COMMENT ON COLUMN registro_llamada.posicion_lista IS 'Posición del candidato en la lista recomendada.';
COMMENT ON COLUMN registro_llamada.telefono_llamado IS 'Número marcado, como estaba en ese momento. Con NUMERO_INVALIDO, el contacto se muestra como inválido mientras su teléfono vigente siga siendo este.';
COMMENT ON COLUMN registro_llamada.resultado IS 'Valores permitidos según la cola: ver chk_rl_resultado.';
COMMENT ON COLUMN registro_llamada.observacion IS 'Nota libre del operador sobre la llamada.';
COMMENT ON COLUMN registro_llamada.fecha_hora IS 'Cuándo se registró la llamada.';
COMMENT ON TABLE registro_llamada IS 'Resultado de cada llamada de las tres colas. Sin UPDATE ni DELETE. El número llamado es el contacto de la cita (cuenta titular o, sin ella, el paciente).';
COMMENT ON COLUMN candidato_postergado.id_oportunidad IS 'Bloque liberado en cuya lista se posterga al candidato.';
COMMENT ON COLUMN candidato_postergado.id_cita IS 'Cita del candidato postergado (la que adelantaría).';
COMMENT ON COLUMN candidato_postergado.id_cuenta_operador IS 'Recepcionista que lo movió.';
COMMENT ON COLUMN candidato_postergado.postergado_en IS 'Cuándo se movió. Ordena a los postergados entre sí, detrás de los no postergados; no altera la antigüedad de inscripción.';
