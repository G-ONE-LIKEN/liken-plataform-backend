-- RF002.001.001: la descripción del proyecto es obligatoria.
-- Rellenar filas existentes sin descripción antes de agregar la restricción.
UPDATE projects SET description = 'Sin descripción' WHERE description IS NULL;

ALTER TABLE projects ALTER COLUMN description SET NOT NULL;
