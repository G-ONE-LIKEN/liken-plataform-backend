-- Renombra el tier BASIC → BRONZE para alinear con la nomenclatura definitiva.
-- También actualiza el default del schema para que nuevos registros usen BRONZE.

UPDATE users SET tier = 'BRONZE' WHERE tier = 'BASIC';
ALTER TABLE users ALTER COLUMN tier SET DEFAULT 'BRONZE';
