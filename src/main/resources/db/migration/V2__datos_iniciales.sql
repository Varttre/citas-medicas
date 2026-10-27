-- Datos iniciales del consultorio: roles, especialidades, tipos de consulta, parámetros y plazos de cada transición.
-- Las tablas con id IDENTITY se insertan sin id; las referencias se resuelven con subconsultas por nombre.
-- Los usuarios de prueba no van aquí: los crea DatosDemo al arrancar con el perfil dev.

INSERT INTO rol (nombre) VALUES
    ('PACIENTE'),
    ('MEDICO'),
    ('RECEPCION'),
    ('ADMINISTRADOR'),
    ('SUPERADMIN');

INSERT INTO especialidad (nombre, descripcion, activo) VALUES
    ('Medicina General', 'Atención general de adultos', TRUE),
    ('Cardiología', 'Corazón y sistema circulatorio', TRUE),
    ('Pediatría', 'Atención de niños y adolescentes', TRUE);

INSERT INTO tipo_consulta (nombre, duracion_defecto, incremento_min, ajuste_medico, ajuste_recepcion,
                           reservable_web, modalidad_permitida, activo) VALUES
    ('Consulta', 30, NULL, TRUE, TRUE, TRUE, 'AMBAS', TRUE),
    ('Primera cita', 45, NULL, TRUE, TRUE, FALSE, 'PRESENCIAL', TRUE),
    ('Seguimiento', 30, NULL, TRUE, TRUE, FALSE, 'AMBAS', TRUE),
    ('Procedimiento menor', 60, NULL, FALSE, FALSE, FALSE, 'PRESENCIAL', TRUE);

INSERT INTO parametro_consultorio (id_parametro, incremento_min, politica_proteccion, minutos_protegidos,
                                   ventana_liberacion_h, bloqueo_temporal_min, n_ventana_estimacion,
                                   anticipacion_confirm_h, vigencia_indicada_dias, mostrar_penalizacion,
                                   texto_penalizacion, max_intentos_login, minutos_bloqueo_login, whatsapp_numero,
                                   dias_recordar_sugerencia, id_tipo_web_defecto)
VALUES (1, 15, 'NUNCA', 120, 48, 5, 30, 48, 90, FALSE, NULL, 5, 15, NULL, 7,
        (SELECT id_tipo_consulta FROM tipo_consulta WHERE nombre = 'Consulta'));

-- Ventana de cada transición en minutos respecto del inicio de la cita (NULL = sin límite por ese lado).
-- hasta_fin_dia: 0 = hasta el fin del mismo día, 1 = hasta el fin del día siguiente.
INSERT INTO plazo_transicion (transicion, minutos_desde, minutos_hasta, hasta_fin_dia) VALUES
    ('NO_ASISTIO', 5, NULL, 0),
    ('RETIRO', NULL, NULL, 0),
    ('CANCELADA_POR_PACIENTE', NULL, 0, NULL),
    ('CANCELADA_POR_CLINICA', NULL, NULL, NULL),
    ('CONFIRMADA', NULL, 0, NULL),
    ('ASISTIO', -30, NULL, 0),
    ('REALIZADA', NULL, NULL, 1);
