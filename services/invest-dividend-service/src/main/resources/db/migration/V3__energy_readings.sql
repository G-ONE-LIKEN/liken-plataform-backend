-- =============================================================================
-- V3: Oracle → dividendos automaticos.
--
-- * `energy_reading_log` — auditoria por-lectura de la energia (kWh) reportada
--   por el oracle IoT para cada proyecto. event_id es derivado de
--   (projectId, readingTimestamp) en el consumer, sirve de idempotencia.
-- * `project_energy_accumulator` — saldo pendiente por proyecto. Se acumula
--   USDC a tarifa PPA fija hasta cruzar el umbral, ahi se publica
--   dividends.deposit_requested y se espera la confirmacion on-chain antes de
--   resetear (dejandolo "in-flight" mientras tanto).
-- =============================================================================

CREATE TABLE energy_reading_log (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    kwh NUMERIC(18,4) NOT NULL CHECK (kwh >= 0),
    revenue_usdc NUMERIC(20,6) NOT NULL CHECK (revenue_usdc >= 0),
    recorded_at TIMESTAMP NOT NULL,
    event_id VARCHAR(120) NOT NULL UNIQUE
);

CREATE INDEX idx_energy_reading_project ON energy_reading_log(project_id);
CREATE INDEX idx_energy_reading_recorded_at ON energy_reading_log(recorded_at);

CREATE TABLE project_energy_accumulator (
    project_id BIGINT PRIMARY KEY,
    pending_kwh NUMERIC(20,4) NOT NULL DEFAULT 0,
    pending_usdc NUMERIC(20,6) NOT NULL DEFAULT 0,
    in_flight_usdc NUMERIC(20,6) NOT NULL DEFAULT 0,
    last_flushed_at TIMESTAMP
);
