ALTER TABLE projects
    ADD COLUMN soft_cap          NUMERIC(20,4),
    ADD COLUMN hard_cap          NUMERIC(20,4),
    ADD COLUMN soft_cap_deadline DATE,
    ADD COLUMN expected_open_date DATE,
    ADD COLUMN raised_amount     NUMERIC(20,4) NOT NULL DEFAULT 0,
    ADD COLUMN expected_annual_production_mwh NUMERIC(18,4);
