-- src/main/resources/db/migration/V4__add_token_price_to_projects.sql

-- Se agrega el precio unitario del token al proyecto.
-- Este valor es fijado por el desarrollador al publicar el proyecto
-- y es el que se usa para calcular el monto total de cada inversion.

ALTER TABLE projects
    ADD COLUMN token_price NUMERIC(19, 2) NOT NULL DEFAULT 0.00;

-- La constraint evita proyectos con precio cero o negativo.
ALTER TABLE projects
    ADD CONSTRAINT chk_token_price_positive CHECK (token_price > 0);