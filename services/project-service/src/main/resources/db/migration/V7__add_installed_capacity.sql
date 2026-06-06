-- V7__add_installed_capacity.sql
-- Capacidad instalada en MW por proyecto.
-- Necesaria para que el oracle-service pueda simular
-- la generación de energía con curva solar realista.

ALTER TABLE projects
    ADD COLUMN installed_capacity_mw NUMERIC(10,4);