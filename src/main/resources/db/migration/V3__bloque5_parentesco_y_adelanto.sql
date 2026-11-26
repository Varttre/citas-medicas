-- Bloque 5 de la tanda 2: parentesco del tercero y reglas de «Acepto ser llamado».
-- Fuente: modelo-datos.json v3.2. V1 y V2 ya están en main y no se editan.

-- 1. Parentesco del tercero con el titular de la cuenta (opcional)
ALTER TABLE cuenta_paciente ADD COLUMN parentesco VARCHAR(10);
ALTER TABLE cuenta_paciente ADD CONSTRAINT chk_cp_parentesco
    CHECK (parentesco IS NULL OR parentesco IN ('HIJO','MADRE','PADRE','CONYUGE','HERMANO','ABUELO','NIETO','OTRO'));
COMMENT ON COLUMN cuenta_paciente.parentesco IS 'Relación del tercero con el titular de la cuenta: HIJO, MADRE, PADRE, CONYUGE, HERMANO, ABUELO, NIETO, OTRO. Opcional; se ve en «Mis terceros» (RC-046).';

-- 2. Horas mínimas entre sacar la cita y su inicio para poder activar «Acepto ser llamado»
ALTER TABLE parametro_consultorio ADD COLUMN min_horas_adelanto SMALLINT NOT NULL DEFAULT 24;
ALTER TABLE parametro_consultorio ALTER COLUMN min_horas_adelanto DROP DEFAULT;
ALTER TABLE parametro_consultorio DROP CONSTRAINT chk_param_rangos;
ALTER TABLE parametro_consultorio ADD CONSTRAINT chk_param_rangos
    CHECK (minutos_protegidos >= 0 AND ventana_liberacion_h >= 0 AND bloqueo_temporal_min > 0
           AND anticipacion_confirm_h >= 0 AND vigencia_indicada_dias > 0 AND max_intentos_login > 0
           AND minutos_bloqueo_login > 0 AND dias_recordar_sugerencia > 0 AND min_horas_adelanto >= 0);
COMMENT ON COLUMN parametro_consultorio.min_horas_adelanto IS 'Horas mínimas entre sacar la cita y su inicio para que pueda activar «Acepto ser llamado». Se evalúa al sacarla (y al reprogramarla) y queda fijado en CITA.elegible_adelanto. Valor inicial: 24 (RC-046).';

-- 3. Elegibilidad fijada al sacar la cita. Las existentes: elegibles si se sacaron con 24 h o más de anticipación,
--    o si ya tienen la preferencia (no se le quita a nadie lo que ya activó).
ALTER TABLE cita ADD COLUMN elegible_adelanto BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE cita c SET elegible_adelanto = (c.inicio - c.creado_en >= INTERVAL '24 hours')
    OR EXISTS (SELECT 1 FROM preferencia_disponibilidad p WHERE p.id_cita = c.id_cita);
COMMENT ON COLUMN cita.elegible_adelanto IS 'Si la cita puede activar «Acepto ser llamado»: se fija al sacarla (había al menos PARAMETRO_CONSULTORIO.min_horas_adelanto horas hasta su inicio) y se vuelve a evaluar al reprogramarla. Cambiar el parámetro no la modifica. En la base tiene DEFAULT FALSE: una cita que no pasa por el servicio no puede activarlo (RC-046).';

-- 4. «Hasta cuántos días antes»: al menos 1 (no más de los que faltan lo valida el servicio)
UPDATE preferencia_disponibilidad SET dias_previos = 1 WHERE dias_previos < 1;
ALTER TABLE preferencia_disponibilidad DROP CONSTRAINT chk_pref_rangos;
ALTER TABLE preferencia_disponibilidad ADD CONSTRAINT chk_pref_rangos CHECK (min_llegada_min >= 0 AND dias_previos >= 1);
COMMENT ON COLUMN preferencia_disponibilidad.dias_previos IS 'Hasta cuántos días antes de su cita acepta adelantar. Al menos 1 y no más de los días que faltan para la cita al guardarla (esto último lo valida el servicio, RC-046).';
