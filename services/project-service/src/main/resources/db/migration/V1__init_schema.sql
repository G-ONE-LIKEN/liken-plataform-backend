CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    energy_type VARCHAR(50) NOT NULL,
    province VARCHAR(100),
    country VARCHAR(100) DEFAULT 'Argentina',
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    installed_capacity_mw DECIMAL(15,4),
    total_tokens DECIMAL(20,8) NOT NULL,
    token_price DECIMAL(15,4) NOT NULL CHECK (token_price > 0),
    minimum_investment DECIMAL(15,4),
    expected_annual_yield DECIMAL(5,2),
    start_date DATE,
    end_date DATE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE project_metrics (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    energy_generated_kwh DECIMAL(20,4) NOT NULL,
    revenue_generated DECIMAL(20,4) NOT NULL,
    source VARCHAR(100),
    recorded_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE project_documents (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    document_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100),
    file_size_bytes BIGINT,
    uploaded_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    tokens_amount DECIMAL(20,8) NOT NULL CHECK (tokens_amount >= 0),
    last_updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, project_id)
);

CREATE INDEX idx_projects_owner   ON projects(owner_id);
CREATE INDEX idx_projects_state   ON projects(state);
CREATE INDEX idx_metrics_project  ON project_metrics(project_id);
CREATE INDEX idx_holdings_user    ON user_holdings(user_id);
CREATE INDEX idx_holdings_project ON user_holdings(project_id);
