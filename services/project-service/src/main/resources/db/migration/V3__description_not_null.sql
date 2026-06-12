-- RF002.001.001: la descripcion del proyecto es obligatoria.
-- Rellenar filas existentes sin descripcion antes de agregar la restriccion.
UPDATE projects SET description = 'Sin descripcion' WHERE description IS NULL;

ALTER TABLE projects ALTER COLUMN description SET NOT NULL;
