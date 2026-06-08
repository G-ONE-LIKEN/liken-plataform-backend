# project-service

Microservicio de la plataforma LIKEN responsable de la gestión de proyectos de inversión en energía renovable.

## Responsabilidades

- CRUD de proyectos con ciclo de vida controlado (estados de negocio + estado on-chain).
- **Workflow de aprobación**: un developer crea el proyecto (queda en `PENDING_APPROVAL`); un ADMIN lo aprueba o rechaza.
- **Publicación on-chain**: al publicar un proyecto aprobado, delega en `blockchain-service` el deploy del `OfferingContract` y procesa los callbacks de éxito/fallo (`OnChainStatus`).
- Upload y gestión de documentos en Google Cloud Storage (ver ADR-0016).
- Registro de métricas de rendimiento.
- Mantiene los **holdings** por proyecto a partir de eventos de compra/venta y transiciona el estado del proyecto según eventos on-chain.
- Publicación de eventos a Kafka para notification e invest-dividend-service.

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka |
| Almacenamiento | Google Cloud Storage (fake-gcs-server en local) |
| Seguridad | Spring Security + `GatewayHeaderAuthFilter` (ADR-0004) |
| Tests | JUnit 5 + Mockito / Testcontainers |

## Dominio

```
com.plataforma.projects/
├── config/       # SecurityConfig, KafkaConfig, GcsConfig
├── controller/   # ProjectController, MetricController, DocumentController
├── dto/
├── event/        # Publicadores y consumidores Kafka
├── exception/
├── model/        # Entidades JPA + enums de estado
├── repository/
├── security/     # GatewayHeaderAuthFilter (ADR-0004)
└── service/
```

## Endpoints

### Públicos (vía gateway)

| Método | Path | Permiso | Descripción |
|--------|------|---------|-------------|
| GET | `/api/projects` | Público | Listar proyectos (filtros, paginación) |
| GET | `/api/projects/{id}` | Público | Detalle de un proyecto |
| POST | `/api/projects` | `project:create` | Crear proyecto (inicia en `PENDING_APPROVAL`) |
| GET | `/api/projects/mine` | `project:create` | Proyectos del developer autenticado |
| GET | `/api/projects/pending-approval` | `ADMIN` | Proyectos a la espera de aprobación |
| POST | `/api/projects/{id}/approve` | `ADMIN` | Aprobar (pasa a `DRAFT`) |
| POST | `/api/projects/{id}/reject` | `ADMIN` | Rechazar (con motivo) |
| PUT | `/api/projects/{id}` | `project:update` + owner | Editar metadata |
| DELETE | `/api/projects/{id}` | `project:delete` + owner | Soft delete |
| PUT | `/api/projects/{id}/state` | `project:update` + owner | Cambiar estado (incl. publicar → deploy on-chain) |
| GET | `/api/projects/{id}/holders` | `project:read` | Listar holders |
| GET | `/api/projects/{id}/metrics` | Público | Historial de métricas |
| POST | `/api/projects/{id}/metrics` | `project:update` + owner | Registrar métrica |
| GET | `/api/projects/{id}/documents` | `project:read` | Listar documentos |
| POST | `/api/projects/{id}/documents` | `project:update` + owner | Upload → V4 signed URL de GCS |
| DELETE | `/api/projects/{id}/documents/{docId}` | `project:delete` + owner | Eliminar documento |

### Internos (red privada, sin JWT)

| Método | Path | Usado por | Descripción |
|--------|------|-----------|-------------|
| GET | `/internal/projects/offering-contracts` | blockchain-service | Lista de Offerings a indexar (`offeringContractAddress != null`) |
| POST | `/internal/projects/publication-success` | blockchain-service | Callback de deploy exitoso (registryProjectId, address, tx) |
| POST | `/internal/projects/publication-failure` | blockchain-service | Callback de deploy fallido (errorMessage) |

## Ciclo de vida de un proyecto

`ProjectState` (estado de negocio) es ortogonal al `RoundState`/`OnChainStatus` (estado on-chain de la ronda primaria).

```
PENDING_APPROVAL → DRAFT → PRE_OPEN → OPEN → CLOSED
        │                     │
        └──→ CANCELLED  ←──────┘
```

| Transición | Quién la dispara |
|---|---|
| `PENDING_APPROVAL → DRAFT` | ADMIN aprueba la propuesta. |
| `DRAFT → PRE_OPEN` | owner/admin publica → deploya el `OfferingContract` (vía blockchain-service). |
| `PRE_OPEN → OPEN` | **automática on-chain**: evento `RoundFinalized` (soft cap alcanzado). |
| `PRE_OPEN → CANCELLED` | **automática on-chain**: evento `RoundFailed`. |
| `OPEN → CLOSED` | owner/admin da de baja el proyecto. |
| `* (no final) → CANCELLED` | manual; desde `OPEN` solo ADMIN. |

- `PENDING_APPROVAL` y `DRAFT` son pre-chain (no visibles al inversor).
- `PRE_OPEN` ↔ Registry `FUNDING` (precio earlyBird); `OPEN` ↔ Registry `ACTIVE` (precio standard + dividendos); `CLOSED` ↔ Registry `PAUSED`.
- `CLOSED` y `CANCELLED` son estados finales.
- `OnChainStatus`: `NOT_DEPLOYED → DEPLOYING → DEPLOYED` / `FAILED` (lo actualizan los callbacks de publicación).

## Autorización por rol

| Operación | ADMIN | DEVELOPER (owner) | DEVELOPER | INVESTOR |
|-----------|-------|-------------------|-----------|----------|
| Ver proyectos / métricas / documentos | ✅ | ✅ | ✅ | ✅ |
| Crear proyecto | ✅ | ✅ | ✅ | ❌ |
| Editar / subir doc / registrar métrica | ✅ | ✅ | ❌ | ❌ |
| Cambiar estado / cancelar (no OPEN) | ✅ | ✅ | ❌ | ❌ |
| Cancelar desde OPEN | ✅ | ❌ | ❌ | ❌ |

## Eventos Kafka

**Publica:**

| Tópico | Cuándo |
|--------|--------|
| `projects.created` | Al crear un proyecto |
| `projects.pending_approval` | Al quedar a la espera de aprobación (notifica a admins) |
| `projects.approved` | Al aprobar un proyecto |
| `projects.rejected` | Al rechazar un proyecto |
| `projects.state_changed` | Al cambiar de estado |
| `projects.metrics_updated` | Al registrar una métrica |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `investment.token_purchased` | blockchain-service | Actualizar holdings y recaudación |
| `projects.round_finalized` | blockchain-service | `PRE_OPEN → OPEN` (ronda exitosa) |
| `projects.round_failed` | blockchain-service | `PRE_OPEN → CANCELLED` (ronda fallida) |
| `user.wallet_linked` | user-service | Reconciliar holders por wallet |
| `marketplace.order_matched` | marketplace-service | Actualizar holdings tras venta P2P (pendiente) |

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8082` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/project_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
| `GCP_PROJECT_ID` | `liken-dev` | ID del proyecto en GCP |
| `GCS_BUCKET_NAME` | `liken-documents` | Bucket de GCS para documentos |
| `GCS_EMULATOR_HOST` | `http://localhost:4443` | Endpoint de fake-gcs-server (vacío en GKE → Workload Identity) |
| `FRONTEND_URL` | `http://localhost:5173` | Origen CORS |

## Levantar en local

```bash
# Desde la raíz del repo — levanta toda la plataforma
docker compose up --build

# Solo infraestructura + este servicio
docker compose up postgres kafka fake-gcs fake-gcs-init -d
cd services/project-service
mvn spring-boot:run
```

## Tests

```bash
mvn test                # Unit tests (H2, sin Docker)
mvn test -Pintegration  # Integration tests (requiere Postgres local)
```
