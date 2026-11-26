-- Historial de demostración (solo perfil dev). Lo ejecuta HistorialDemo al arrancar, una sola vez: si ya hay alguna
-- cita REALIZADA no hace nada. Las fechas son relativas a hoy (hora de Lima), así la demostración siempre tiene
-- datos de las últimas cuatro semanas y de los próximos días.
-- Crea: una médica de Pediatría, pacientes de ejemplo (uno es hijo de la paciente demo), citas pasadas en todos los
-- estados, horarios liberados recuperados y vencidos, reprogramaciones, citas indicadas, llamadas de confirmación,
-- citas futuras con «Acepto ser llamado», un horario liberado abierto con candidatos y 15 pacientes en espera (3 por
-- médico) para que una cancelación en la demostración, a cualquier hora, tenga a quién llamar.
DO $$
DECLARE
    v_hoy        date := (now() AT TIME ZONE 'America/Lima')::date;
    v_ahora      timestamp := (now() AT TIME ZONE 'America/Lima')::timestamp(0);
    v_rec        bigint;
    v_tit        bigint;
    v_ram        bigint;
    v_pon        bigint;
    v_qui        bigint;
    v_lucia      bigint;
    v_mateo      bigint;
    v_pacs       bigint[];
    v_dia        date;
    v_off        int;
    v_k          int;
    v_med        bigint;
    v_esp        smallint;
    v_hora       time;
    v_ini        timestamp;
    v_fin        timestamp;
    v_pac        bigint;
    v_tipo       smallint;
    v_dur        int;
    v_estado     varchar;
    v_canal      varchar;
    v_titular    bigint;
    v_cita       bigint;
    v_cob        bigint;
    v_op         bigint;
    v_llam       bigint;
    v_n          int := 0;
    v_slot       record;
    v_fila       record;
    -- Pacientes en espera (sección 6): siempre hay candidatos para un cupo que se libere en la demostración
    v_nombres    text[] := ARRAY['Carlos','Lucía','Miguel','Patricia','Javier','Gabriela','Ricardo','Sofía',
                                 'Fernando','Carmen','Diego','Isabel','Martín','Elena','Andrés'];
    v_apellidos  text[] := ARRAY['Mendoza Paz','Ramos Díaz','Chávez Soto','Vargas Peña','Rojas Luna','Navarro Gil',
                                 'Salinas Cruz','Medina Rey','Aguilar Mora','Benites Lara','Campos Vera',
                                 'Delgado Ríos','Espinoza Leal','Fuentes Ruiz','Guerrero Sanz'];
    v_doc        varchar;
BEGIN
    IF EXISTS (SELECT 1 FROM cita WHERE estado = 'REALIZADA') THEN
        RETURN;
    END IF;

    SELECT id_cuenta INTO v_rec FROM cuenta WHERE correo = 'recepcion@consultorio.pe';
    SELECT id_cuenta INTO v_tit FROM cuenta WHERE correo = 'paciente@consultorio.pe';
    SELECT m.id_medico INTO v_ram FROM medico m JOIN cuenta c USING (id_cuenta) WHERE c.correo = 'medico1@consultorio.pe';
    SELECT m.id_medico INTO v_pon FROM medico m JOIN cuenta c USING (id_cuenta) WHERE c.correo = 'medico2@consultorio.pe';
    IF v_rec IS NULL OR v_tit IS NULL OR v_ram IS NULL OR v_pon IS NULL THEN
        RAISE NOTICE 'Historial de demostración omitido: faltan los usuarios de prueba';
        RETURN;
    END IF;
    SELECT id_paciente_propio INTO v_lucia FROM cuenta WHERE id_cuenta = v_tit;

    -- 1. Médica de Pediatría (la especialidad no tenía médico), con su horario y las duraciones por defecto
    SELECT m.id_medico INTO v_qui FROM medico m JOIN cuenta c USING (id_cuenta) WHERE c.correo = 'medico3@consultorio.pe';
    IF v_qui IS NULL THEN
        INSERT INTO cuenta (correo, nombre_visible, password_hash, id_rol, estado, intentos_fallidos, creado_en)
        SELECT 'medico3@consultorio.pe', NULL, password_hash, (SELECT id_rol FROM rol WHERE nombre = 'MEDICO'),
               'ACTIVA', 0, v_ahora
          FROM cuenta WHERE id_cuenta = v_rec;
        INSERT INTO medico (id_cuenta, nombres, apellidos, activo)
        VALUES ((SELECT id_cuenta FROM cuenta WHERE correo = 'medico3@consultorio.pe'), 'Sofía', 'Quispe Huamán', TRUE)
        RETURNING id_medico INTO v_qui;
        INSERT INTO medico_especialidad (id_medico, id_especialidad)
        SELECT v_qui, id_especialidad FROM especialidad WHERE nombre = 'Pediatría';
        INSERT INTO medico_tipo_consulta (id_medico, id_tipo_consulta, duracion_configurada)
        SELECT v_qui, id_tipo_consulta, duracion_defecto FROM tipo_consulta WHERE activo;
        INSERT INTO horario_version (id_medico, vigente_desde) VALUES (v_qui, v_hoy - 60);
        INSERT INTO franja_horaria (id_horario_version, dia_semana, hora_inicio, hora_fin)
        SELECT hv.id_horario_version, d, TIME '14:00', TIME '23:00'
          FROM horario_version hv, generate_series(1, 7) d WHERE hv.id_medico = v_qui;
    END IF;

    -- 2. Pacientes de ejemplo (sin cuenta web: los registró recepción) y el hijo de la paciente demo
    INSERT INTO paciente (tipo_documento, nro_documento, nombres, apellidos, fecha_nacimiento, telefono) VALUES
        ('DNI', '40112233', 'Jorge', 'Salazar Mendoza', DATE '1978-03-02', '912345670'),
        ('DNI', '41223344', 'María', 'Torres Llanos', DATE '1985-07-21', '923456781'),
        ('DNI', '42334455', 'Rosa', 'Huamán Quispe', DATE '1959-11-30', '934567892'),
        ('DNI', '43445566', 'Pedro', 'Castillo Ruiz', DATE '1992-01-15', '945678903'),
        ('DNI', '44556677', 'Ana', 'Flores Paredes', DATE '1969-09-09', '956789014'),
        ('DNI', '45667788', 'Luis', 'Gómez Vargas', DATE '2001-04-27', '967890125'),
        ('DNI', '46778899', 'Valeria', 'Ríos Castro', DATE '1996-12-03', '978901236'),
        ('DNI', '78901234', 'Mateo', 'Vega Ríos', DATE '2018-06-10', NULL)
    ON CONFLICT (tipo_documento, nro_documento) DO NOTHING;
    SELECT id_paciente INTO v_mateo FROM paciente WHERE nro_documento = '78901234';
    INSERT INTO cuenta_paciente (id_cuenta, id_paciente, consentimiento_en)
    SELECT v_tit, v_mateo, v_ahora - INTERVAL '40 days'
     WHERE NOT EXISTS (SELECT 1 FROM cuenta_paciente WHERE id_cuenta = v_tit AND id_paciente = v_mateo);
    SELECT array_agg(id_paciente ORDER BY nro_documento) INTO v_pacs
      FROM paciente WHERE nro_documento IN ('40112233','41223344','42334455','43445566','44556677','45667788','46778899');

    -- 3. Citas pasadas: últimas 4 semanas, días hábiles. Cada (día, médico, hora) recibe un destino según un patrón fijo
    FOR v_off IN 1..28 LOOP
        v_dia := v_hoy - v_off;
        CONTINUE WHEN extract(isodow FROM v_dia) > 5;
        FOR v_slot IN
            SELECT * FROM (VALUES
                (v_ram, TIME '09:00', 1), (v_ram, TIME '10:00', 2), (v_ram, TIME '16:00', 3),
                (v_pon, TIME '11:00', 4), (v_pon, TIME '12:00', 5),
                (v_qui, TIME '14:00', 6), (v_qui, TIME '15:00', 7)) s(med, hora, n)
        LOOP
            CONTINUE WHEN v_slot.med = v_pon AND extract(isodow FROM v_dia) NOT IN (1, 3, 5);
            v_med := v_slot.med;
            SELECT id_especialidad INTO v_esp FROM medico_especialidad WHERE id_medico = v_med LIMIT 1;
            v_k := (v_off * 3 + v_slot.n * 7) % 13;
            v_tipo := CASE WHEN v_k = 4 THEN 2 WHEN v_k IN (6, 9) THEN 3 ELSE 1 END;
            v_dur := CASE v_tipo WHEN 2 THEN 45 ELSE 30 END;
            v_ini := v_dia + v_slot.hora;
            v_fin := v_ini + make_interval(mins => v_dur);
            IF v_med = v_qui THEN
                v_pac := CASE WHEN v_k % 3 = 0 THEN v_mateo ELSE v_pacs[1 + (v_off + v_slot.n) % 7] END;
            ELSIF v_k = 7 THEN
                v_pac := v_lucia;
            ELSE
                v_pac := v_pacs[1 + (v_off * 2 + v_slot.n) % 7];
            END IF;
            v_titular := CASE WHEN v_pac IN (v_lucia, v_mateo) THEN v_tit END;
            v_canal := CASE WHEN v_titular IS NOT NULL THEN 'WEB'
                            WHEN v_k % 4 = 0 THEN 'PRESENCIAL' WHEN v_k % 4 = 1 THEN 'WHATSAPP' ELSE 'TELEFONO' END;
            v_estado := CASE WHEN v_k IN (0, 11) THEN 'NO_ASISTIO'
                             WHEN v_k IN (1, 8) THEN 'CANCELADA_POR_PACIENTE'
                             WHEN v_k = 12 THEN 'CANCELADA_POR_CLINICA'
                             ELSE 'REALIZADA' END;

            INSERT INTO cita (id_paciente, id_cuenta_titular, id_medico, id_especialidad, id_tipo_consulta, inicio, fin,
                              estado, modalidad, canal_origen, es_primera_vez, hora_llegada, inicio_real, fin_real,
                              indicaciones, creado_en)
            VALUES (v_pac, v_titular, v_med, v_esp, v_tipo, v_ini, v_fin, v_estado,
                    CASE WHEN v_k = 10 AND v_tipo <> 2 THEN 'TELECONSULTA' ELSE 'PRESENCIAL' END,
                    v_canal, v_tipo = 2,
                    CASE WHEN v_estado = 'REALIZADA' THEN v_ini - make_interval(mins => 5 + v_k) END,
                    CASE WHEN v_estado = 'REALIZADA' THEN v_ini + make_interval(mins => GREATEST(0, (v_off * 7 + v_slot.n) % 16 - 5)) END,
                    CASE WHEN v_estado = 'REALIZADA' THEN v_ini + make_interval(mins => GREATEST(0, (v_off * 7 + v_slot.n) % 16 - 5)
                                                                + v_dur - 8 + (v_off * 5 + v_slot.n * 3) % 21) END,
                    CASE WHEN v_estado = 'REALIZADA' THEN 'Control de presión y revisión de exámenes.' END,
                    v_ini - make_interval(days => 3 + v_k % 5))
            RETURNING id_cita INTO v_cita;

            INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_nuevo, inicio_nuevo, fin_nuevo, fecha_hora)
            VALUES (v_cita, COALESCE(v_titular, v_rec), 'CREACION', 'PROGRAMADA', v_ini, v_fin,
                    v_ini - make_interval(days => 3 + v_k % 5));

            -- Reprogramada una vez (y dos, a veces) antes de su fecha final
            IF v_k IN (5, 9) THEN
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, inicio_anterior, fin_anterior, inicio_nuevo,
                                         fin_nuevo, fecha_hora)
                VALUES (v_cita, COALESCE(v_titular, v_rec), 'REPROGRAMACION', v_ini - INTERVAL '1 day',
                        v_fin - INTERVAL '1 day', v_ini, v_fin, v_ini - INTERVAL '2 days');
            END IF;
            IF v_k = 9 THEN
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, inicio_anterior, fin_anterior, inicio_nuevo,
                                         fin_nuevo, fecha_hora)
                VALUES (v_cita, v_rec, 'REPROGRAMACION', v_ini + INTERVAL '2 hours', v_fin + INTERVAL '2 hours',
                        v_ini, v_fin, v_ini - INTERVAL '1 day');
            END IF;

            IF v_estado = 'REALIZADA' THEN
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_anterior, estado_nuevo, fecha_hora)
                VALUES (v_cita, v_rec, 'CAMBIO_ESTADO', 'PROGRAMADA', 'ASISTIO', v_ini - make_interval(mins => 5 + v_k)),
                       (v_cita, (SELECT id_cuenta FROM medico WHERE id_medico = v_med), 'CAMBIO_ESTADO', 'ASISTIO',
                        'REALIZADA', v_fin + INTERVAL '20 minutes');
            ELSIF v_estado = 'NO_ASISTIO' THEN
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_anterior, estado_nuevo, fecha_hora)
                VALUES (v_cita, v_rec, 'CAMBIO_ESTADO', 'PROGRAMADA', 'NO_ASISTIO', v_ini + INTERVAL '15 minutes');
            ELSE
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_anterior, estado_nuevo, motivo, fecha_hora)
                VALUES (v_cita, COALESCE(v_titular, v_rec), 'CAMBIO_ESTADO', 'PROGRAMADA', v_estado,
                        CASE WHEN v_estado = 'CANCELADA_POR_CLINICA' THEN 'El médico no pudo atender ese día'
                             ELSE 'Viaje imprevisto' END,
                        v_ini - INTERVAL '1 day');
                -- El horario liberado: la mitad se recupera llamando a otro paciente, el resto vence
                INSERT INTO oportunidad (id_cita_origen, id_medico, inicio, fin, estado, creada_en, cerrada_en)
                VALUES (v_cita, v_med, v_ini, v_fin, 'ABIERTA', v_ini - INTERVAL '1 day', NULL)
                RETURNING id_oportunidad INTO v_op;
                IF v_off % 2 = 0 OR v_k = 8 THEN
                    v_pac := v_pacs[1 + (v_off + 3) % 7];
                    INSERT INTO cita (id_paciente, id_medico, id_especialidad, id_tipo_consulta, inicio, fin, estado,
                                      modalidad, canal_origen, es_primera_vez, hora_llegada, inicio_real, fin_real,
                                      creado_en)
                    VALUES (v_pac, v_med, v_esp, 1, v_ini, v_ini + INTERVAL '30 minutes', 'REALIZADA', 'PRESENCIAL',
                            'TELEFONO', FALSE, v_ini - INTERVAL '10 minutes', v_ini + INTERVAL '3 minutes',
                            v_ini + INTERVAL '31 minutes', v_ini - INTERVAL '20 hours')
                    RETURNING id_cita INTO v_cob;
                    INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_nuevo, inicio_nuevo, fin_nuevo,
                                             fecha_hora)
                    VALUES (v_cob, v_rec, 'CREACION', 'PROGRAMADA', v_ini, v_ini + INTERVAL '30 minutes',
                            v_ini - INTERVAL '20 hours');
                    INSERT INTO registro_llamada (id_cuenta_operador, tipo_cola, id_cita, id_oportunidad, posicion_lista,
                                                  telefono_llamado, resultado, fecha_hora)
                    SELECT v_rec, 'CUPO_LIBERADO', v_cob, v_op, 1, telefono, 'ACEPTO', v_ini - INTERVAL '20 hours'
                      FROM paciente WHERE id_paciente = v_pac;
                    UPDATE oportunidad SET estado = 'CUBIERTA', cerrada_en = v_ini - INTERVAL '20 hours',
                                           id_cita_cobertura = v_cob
                     WHERE id_oportunidad = v_op;
                ELSE
                    UPDATE oportunidad SET estado = 'EXPIRADA', cerrada_en = v_ini WHERE id_oportunidad = v_op;
                END IF;
            END IF;
            v_n := v_n + 1;
        END LOOP;
    END LOOP;

    -- 4. Citas indicadas por el médico (seguimiento) a partir de citas realizadas
    FOR v_fila IN
        SELECT c.id_cita, c.id_paciente, c.id_medico, c.id_especialidad, c.fin_real, (row_number() OVER (ORDER BY c.inicio))::int AS r
          FROM cita c
         WHERE c.estado = 'REALIZADA' AND c.id_tipo_consulta = 1 AND c.id_medico IN (v_ram, v_qui)
         ORDER BY c.inicio
         LIMIT 8
    LOOP
        IF v_fila.r <= 3 THEN
            -- Agendada: la cita generada es una de seguimiento en los próximos días
            v_dia := v_hoy + 3 + v_fila.r * 2;
            WHILE extract(isodow FROM v_dia) > 5 LOOP v_dia := v_dia + 1; END LOOP;
            v_ini := v_dia + CASE WHEN v_fila.id_medico = v_qui THEN TIME '17:00' ELSE TIME '12:00' END;
            INSERT INTO cita (id_paciente, id_cuenta_titular, id_medico, id_especialidad, id_tipo_consulta, inicio, fin,
                              estado, modalidad, canal_origen, es_primera_vez, creado_en)
            VALUES (v_fila.id_paciente, CASE WHEN v_fila.id_paciente IN (v_lucia, v_mateo) THEN v_tit END,
                    v_fila.id_medico, v_fila.id_especialidad, 3, v_ini, v_ini + INTERVAL '30 minutes', 'PROGRAMADA',
                    'PRESENCIAL', 'CONSULTA_MEDICA', FALSE, v_fila.fin_real)
            RETURNING id_cita INTO v_cita;
            INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_nuevo, inicio_nuevo, fin_nuevo, fecha_hora)
            VALUES (v_cita, v_rec, 'CREACION', 'PROGRAMADA', v_ini, v_ini + INTERVAL '30 minutes', v_fila.fin_real);
            INSERT INTO cita_indicada (id_cita_origen, id_tipo_consulta, duracion, fecha_recomendada, vigente_hasta,
                                       indicaciones, estado, id_cita_generada, creado_en)
            VALUES (v_fila.id_cita, 3, 30, v_dia, v_dia + 30, 'Traer resultados de laboratorio.', 'AGENDADA', v_cita,
                    v_fila.fin_real);
        ELSIF v_fila.r <= 5 THEN
            INSERT INTO cita_indicada (id_cita_origen, id_tipo_consulta, duracion, fecha_recomendada, vigente_hasta,
                                       indicaciones, estado, creado_en)
            VALUES (v_fila.id_cita, 3, 30, v_fila.fin_real::date + 3, v_fila.fin_real::date + 6, 'Control en 3 días.',
                    'VENCIDA', v_fila.fin_real);
        ELSIF v_fila.r = 6 THEN
            INSERT INTO cita_indicada (id_cita_origen, id_tipo_consulta, duracion, fecha_recomendada, vigente_hasta,
                                       indicaciones, estado, motivo_descarte, creado_en)
            VALUES (v_fila.id_cita, 3, 30, v_fila.fin_real::date + 7, v_fila.fin_real::date + 37, 'Control mensual.',
                    'DESCARTADA', 'Se atenderá en otra clínica', v_fila.fin_real);
        ELSE
            INSERT INTO cita_indicada (id_cita_origen, id_tipo_consulta, duracion, fecha_recomendada, vigente_hasta,
                                       indicaciones, estado, creado_en)
            VALUES (v_fila.id_cita, 3, 30, v_hoy + 5, v_hoy + 60, 'Control de seguimiento.', 'PENDIENTE', v_fila.fin_real);
        END IF;
    END LOOP;

    -- 5. Citas futuras: próximos 8 días hábiles. Las de los dos primeros días quedan en la cola de confirmación
    v_off := 0;
    v_dia := v_hoy;
    WHILE v_off < 8 LOOP
        v_dia := v_dia + 1;
        CONTINUE WHEN extract(isodow FROM v_dia) > 5;
        v_off := v_off + 1;
        FOR v_slot IN
            SELECT * FROM (VALUES
                (v_ram, TIME '09:00', 1), (v_ram, TIME '10:00', 2), (v_ram, TIME '16:00', 3),
                (v_pon, TIME '10:00', 4), (v_qui, TIME '14:30', 5), (v_qui, TIME '15:30', 6)) s(med, hora, n)
        LOOP
            CONTINUE WHEN v_slot.med = v_pon AND extract(isodow FROM v_dia) NOT IN (1, 3, 5);
            v_med := v_slot.med;
            SELECT id_especialidad INTO v_esp FROM medico_especialidad WHERE id_medico = v_med LIMIT 1;
            v_k := (v_off * 5 + v_slot.n * 3) % 11;
            CONTINUE WHEN v_k = 4;  -- huecos libres para reservar en la demostración
            v_ini := v_dia + v_slot.hora;
            v_fin := v_ini + INTERVAL '30 minutes';
            IF v_med = v_qui AND v_k % 2 = 0 THEN
                v_pac := v_mateo;
            ELSIF v_off = 3 AND v_slot.n = 3 THEN
                v_pac := v_lucia;
            ELSE
                v_pac := v_pacs[1 + (v_off * 3 + v_slot.n) % 7];
            END IF;
            v_titular := CASE WHEN v_pac IN (v_lucia, v_mateo) THEN v_tit END;
            v_estado := CASE WHEN v_off = 1 AND v_k % 3 = 0 THEN 'CONFIRMADA' ELSE 'PROGRAMADA' END;
            -- Un horario liberado abierto: la cita de las 10:00 de la Dra. Ramírez del segundo día se canceló hoy
            IF v_off = 2 AND v_slot.n = 2 THEN
                v_estado := 'CANCELADA_POR_PACIENTE';
            END IF;
            INSERT INTO cita (id_paciente, id_cuenta_titular, id_medico, id_especialidad, id_tipo_consulta, inicio, fin,
                              estado, modalidad, canal_origen, es_primera_vez, creado_en)
            VALUES (v_pac, v_titular, v_med, v_esp, 1, v_ini, v_fin, v_estado, 'PRESENCIAL',
                    CASE WHEN v_titular IS NOT NULL THEN 'WEB' ELSE 'TELEFONO' END, FALSE,
                    v_ahora - make_interval(days => 2 + v_k))
            RETURNING id_cita INTO v_cita;
            INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_nuevo, inicio_nuevo, fin_nuevo, fecha_hora)
            VALUES (v_cita, COALESCE(v_titular, v_rec), 'CREACION', 'PROGRAMADA', v_ini, v_fin,
                    v_ahora - make_interval(days => 2 + v_k));
            IF v_estado = 'CONFIRMADA' THEN
                INSERT INTO registro_llamada (id_cuenta_operador, tipo_cola, id_cita, telefono_llamado, resultado, fecha_hora)
                SELECT v_rec, 'CONFIRMACION', v_cita, COALESCE(p.telefono, '987654321'), 'CONFIRMO', v_ahora - INTERVAL '3 hours'
                  FROM paciente p WHERE p.id_paciente = v_pac
                RETURNING id_llamada INTO v_llam;
                INSERT INTO cita_evento (id_cita, id_cuenta, id_llamada, tipo_evento, estado_anterior, estado_nuevo, fecha_hora)
                VALUES (v_cita, v_rec, v_llam, 'CAMBIO_ESTADO', 'PROGRAMADA', 'CONFIRMADA', v_ahora - INTERVAL '3 hours');
            ELSIF v_estado = 'CANCELADA_POR_PACIENTE' THEN
                INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_anterior, estado_nuevo, motivo, fecha_hora)
                VALUES (v_cita, v_rec, 'CAMBIO_ESTADO', 'PROGRAMADA', v_estado, 'Avisó por teléfono', v_ahora - INTERVAL '1 hour');
                INSERT INTO oportunidad (id_cita_origen, id_medico, inicio, fin, estado, creada_en)
                VALUES (v_cita, v_med, v_ini, v_fin, 'ABIERTA', v_ahora - INTERVAL '1 hour');
            ELSIF v_off >= 4 AND v_k % 2 = 1 THEN
                -- «Acepto ser llamado si se libera un cupo antes»: candidatos para los horarios liberados
                INSERT INTO preferencia_disponibilidad (id_cita, alcance, min_llegada_min, dias_previos, inscrito_en,
                                                        consentido_en)
                VALUES (v_cita, CASE WHEN v_k % 4 = 1 THEN 'ESPECIALIDAD' ELSE 'MEDICO' END, 60, 30,
                        v_ahora - make_interval(days => 2 + v_k), v_ahora - make_interval(days => 2 + v_k));
            END IF;
        END LOOP;
    END LOOP;

    -- 6. Pacientes en espera: 3 por médico (con cita unas tres semanas después, ya pasada la semana de exposición) que
    --    aceptaron que los llamen si se libera un cupo antes: 15 minutos para llegar y todos los días posibles. Así
    --    cualquier cita que se cancele en la demostración, a la hora que sea, tiene candidatos. El tercero de cada
    --    médico acepta cualquier médico de la especialidad (Cardiología y Medicina General tienen dos). Cada paciente
    --    acepta un solo cupo por día: hay 3 por médico para poder ensayar varias veces el mismo día.
    FOR v_slot IN
        SELECT * FROM (VALUES
            ('medico1@consultorio.pe', TIME '10:00', 1), ('medico2@consultorio.pe', TIME '18:00', 2),
            ('medico3@consultorio.pe', TIME '15:00', 3), ('medico4@consultorio.pe', TIME '16:00', 4),
            ('medico5@consultorio.pe', TIME '09:00', 5)) s(correo, hora, n)
    LOOP
        SELECT m.id_medico INTO v_med FROM medico m JOIN cuenta c USING (id_cuenta) WHERE c.correo = v_slot.correo;
        CONTINUE WHEN v_med IS NULL;
        SELECT id_especialidad INTO v_esp FROM medico_especialidad WHERE id_medico = v_med LIMIT 1;
        FOR v_k IN 1..3 LOOP
            v_off := (v_slot.n - 1) * 3 + v_k;
            v_doc := '500000' || lpad(v_off::text, 2, '0');
            INSERT INTO paciente (tipo_documento, nro_documento, nombres, apellidos, fecha_nacimiento, telefono)
            VALUES ('DNI', v_doc, v_nombres[v_off], v_apellidos[v_off], DATE '1975-01-15' + v_off * 400,
                    '9010000' || lpad(v_off::text, 2, '0'))
            ON CONFLICT (tipo_documento, nro_documento) DO NOTHING;
            SELECT id_paciente INTO v_pac FROM paciente WHERE tipo_documento = 'DNI' AND nro_documento = v_doc;
            v_dia := v_hoy + 19 + v_k;
            v_ini := v_dia + v_slot.hora;
            v_fin := v_ini + INTERVAL '30 minutes';
            INSERT INTO cita (id_paciente, id_medico, id_especialidad, id_tipo_consulta, inicio, fin, estado, modalidad,
                              canal_origen, es_primera_vez, creado_en, elegible_adelanto)
            VALUES (v_pac, v_med, v_esp, 1, v_ini, v_fin, 'PROGRAMADA', 'PRESENCIAL', 'TELEFONO', TRUE,
                    v_ahora - make_interval(days => 2 + v_k), TRUE)
            RETURNING id_cita INTO v_cita;
            INSERT INTO cita_evento (id_cita, id_cuenta, tipo_evento, estado_nuevo, inicio_nuevo, fin_nuevo, fecha_hora)
            VALUES (v_cita, v_rec, 'CREACION', 'PROGRAMADA', v_ini, v_fin, v_ahora - make_interval(days => 2 + v_k));
            INSERT INTO preferencia_disponibilidad (id_cita, alcance, min_llegada_min, dias_previos, inscrito_en,
                                                    consentido_en)
            VALUES (v_cita, CASE WHEN v_k = 3 THEN 'ESPECIALIDAD' ELSE 'MEDICO' END, 15, v_dia - v_hoy,
                    v_ahora - make_interval(days => 2 + v_k), v_ahora - make_interval(days => 2 + v_k));
        END LOOP;
    END LOOP;

    -- 7. «Acepto ser llamado» se fija al sacar la cita (RC-046): las citas futuras del historial se sacaron con días de
    --    anticipación, y las que ya tienen la preferencia la conservan.
    UPDATE cita c SET elegible_adelanto = TRUE
     WHERE c.inicio > v_ahora
       AND (c.inicio - c.creado_en >= INTERVAL '24 hours'
            OR EXISTS (SELECT 1 FROM preferencia_disponibilidad p WHERE p.id_cita = c.id_cita));

    RAISE NOTICE 'Historial de demostración creado: % citas pasadas', v_n;
END $$;
