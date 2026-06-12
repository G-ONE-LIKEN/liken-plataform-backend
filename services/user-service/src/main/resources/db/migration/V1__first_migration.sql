-- src/main/resources/db/migration/V1__first_migration.sql

-- Migracion base que genera las entidades principales y sus relaciones.

-- =========================
-- ROLES
-- =========================
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

-- =========================
-- PERMISSIONS
-- =========================
CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL
);

-- =========================
-- ROLES_PERMISSIONS (N:M)
-- =========================
CREATE TABLE roles_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- =========================
-- USERS
-- =========================
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) UNIQUE,
    password   VARCHAR(255),
    role_id    BIGINT NOT NULL,
    active     BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- =========================
-- PROJECTS
-- =========================
CREATE TABLE projects (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255),
    owner_id          BIGINT NOT NULL,
    state             VARCHAR(50) NOT NULL,
    max_amount_tokens BIGINT,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- =========================
-- USER_PROJECTS (N:M con extra campo)
-- =========================
CREATE TABLE user_projects (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    project_id    BIGINT NOT NULL,
    tokens_amount BIGINT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,
    CONSTRAINT fk_up_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_up_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_project UNIQUE (user_id, project_id)
);

-- =========================
-- INVESTMENTS
-- =========================
CREATE TABLE investments (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    project_id       BIGINT NOT NULL,
    tokens_purchased BIGINT,
    amount_paid      NUMERIC(19,2),
    token_price      NUMERIC(19,2),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP,
    CONSTRAINT fk_inv_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_inv_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- =========================
-- iNDICES (mejorar consultas)
-- =========================
CREATE INDEX idx_users_email           ON users(email);
CREATE INDEX idx_projects_owner        ON projects(owner_id);
CREATE INDEX idx_user_projects_user    ON user_projects(user_id);
CREATE INDEX idx_user_projects_project ON user_projects(project_id);
CREATE INDEX idx_investments_user      ON investments(user_id);
CREATE INDEX idx_investments_project   ON investments(project_id);