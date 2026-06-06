CREATE TABLE energy_readings (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    reading_timestamp TIMESTAMP NOT NULL,
    energy_kwh DECIMAL(18,4) NOT NULL,
    CONSTRAINT uq_project_reading_timestamp UNIQUE (project_id, reading_timestamp)
);

CREATE INDEX idx_energy_readings_project ON energy_readings(project_id);
CREATE INDEX idx_energy_readings_timestamp ON energy_readings(reading_timestamp); 