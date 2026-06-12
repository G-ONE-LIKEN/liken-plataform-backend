-- src/test/resources/data.sql
-- Seed para tests @Tag("unit") con H2 (perfil "test").
--
-- Replica V2__seed_roles_permissions.sql en SQL estandar compatible con H2.
-- Se ejecuta DESPUÉS de que Hibernate crea las tablas gracias a:
--   spring.jpa.defer-datasource-initialization=true  (application-test.properties)
--
-- CONVENCIoN DE PERMISOS: formato "recurso:accion" en minusculas.
-- Debe mantenerse en sincronia con V2__seed_roles_permissions.sql.

-- =========================
-- PERMISSIONS
-- =========================
INSERT INTO permissions (name, description) VALUES
    ('project:read',   'Permiso para leer proyectos'),
    ('project:create', 'Permiso para crear proyectos'),
    ('project:update', 'Permiso para modificar proyectos'),
    ('project:delete', 'Permiso para eliminar proyectos'),
    ('invest:create',  'Permiso para invertir'),
    ('user:read',      'Permiso para leer usuarios'),
    ('user:update',    'Permiso para modificar usuarios'),
    ('user:delete',    'Permiso para eliminar usuarios'),
    ('kyc:review',     'Permiso para aprobar o rechazar verificaciones KYC');

-- =========================
-- ROLES
-- =========================
INSERT INTO roles (name, description, active, created_at) VALUES
    ('BASIC',     'Usuario basico',        true, NOW()),
    ('INVESTOR',  'Usuario inversor',      true, NOW()),
    ('DEVELOPER', 'Usuario desarrollador', true, NOW()),
    ('ADMIN',     'Administrador',         true, NOW());

-- =========================
-- RELACIONES ROLE ↔ PERMISSION
-- =========================

-- BASIC → project:read
INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'BASIC' AND p.name = 'project:read';

-- INVESTOR → project:read, invest:create
INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'INVESTOR'
      AND p.name IN ('project:read', 'invest:create');

-- DEVELOPER → project:read, project:create, project:update
INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'DEVELOPER'
      AND p.name IN ('project:read', 'project:create', 'project:update');

-- ADMIN → todos los permisos
INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'ADMIN';