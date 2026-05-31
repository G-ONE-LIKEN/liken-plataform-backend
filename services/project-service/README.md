# project-service

Microservicio de la plataforma LIKEN responsable de la gestión de proyectos de inversión en energía renovable.

## Responsabilidades

- CRUD de proyectos con ciclo de vida controlado (estados)
- Upload y gestión de documentos en Google Cloud Storage (ver DD014)
- Registro de métricas de rendimiento
- Publicación de eventos a Kafka para notify e invest-dividend-service

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka |
| Almacenamiento | Google Cloud Storage (fake-gcs-server en local) |
| Seguridad | Spring Security + `GatewayHeaderAuthFilter` (DD002) |
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
├── security/     # GatewayHeaderAuthFilter (DD002)
└── service/
```

## Endpoints

| Método | Path | Permiso | Descripción |
|--------|------|---------|-------------|
| GET | `/api/projects` | Público | Listar proyectos (filtros, paginación) |
| GET | `/api/projects/{id}` | Público | Detalle de un proyecto |
| POST | `/api/projects` | `project:create` | Crear proyecto (inicia en DRAFT) |
| PUT | `/api/projects/{id}` | `project:update` + owner | Editar metadata |
| DELETE | `/api/projects/{id}` | `project:delete` + owner | Soft delete |
| PUT | `/api/projects/{id}/state` | `project:update` + owner | Cambiar estado |
| GET | `/api/projects/{id}/holders` | `project:read` | Listar holders |
| GET | `/api/projects/{id}/metrics` | Público | Historial de métricas |
| POST | `/api/projects/{id}/metrics` | `project:update` + owner | Registrar métrica |
| GET | `/api/projects/{id}/documents` | `project:read` | Listar documentos |
| POST | `/api/projects/{id}/documents` | `project:update` + owner | Upload → V4 signed URL de GCS |
| DELETE | `/api/projects/{id}/documents/{docId}` | `project:delete` + owner | Eliminar documento |

## Ciclo de vida de un proyecto

```
DRAFT → PRE_OPEN → OPEN → CLOSED
          ↓          ↓
       CANCELLED  CANCELLED
```

- Progresión secuencial sin retroceso.
- `CANCELLED` es posible desde `DRAFT`, `PRE_OPEN` y `OPEN`.
- Desde `OPEN`, solo un `ADMIN` puede cancelar.
- `CLOSED` y `CANCELLED` son estados finales.

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
| `projects.state_changed` | Al cambiar estado |
| `projects.metrics_updated` | Al registrar métrica |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `investment.token_purchased` | invest-dividend-service | Actualizar holdings |
| `marketplace.order_matched` | marketplace-service | Actualizar holdings tras venta P2P |

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
