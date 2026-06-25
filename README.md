# liken-plataform-backend

Backend de la plataforma LIKEN: marketplace de inversion en proyectos de energia renovable.

## Arquitectura

```
Frontend (3000 / 5173)
      │
      ▼
api-gateway (8090)        ← valida JWT, inyecta headers, rate-limit con Redis
      │
      ├── /api/auth/**            ──▶  auth-service            (8081)
      ├── /api/users/**           ──▶  user-service            (8080)
      ├── /api/roles/**           ──▶  user-service            (8080)
      ├── /api/permissions/**     ──▶  user-service            (8080)
      ├── /api/projects/**        ──▶  project-service         (8082)
      ├── /api/wallets/**         ──▶  wallet-service          (8084)
      ├── /api/investments/**     ──▶  invest-dividend-service (8083)
      ├── /api/dividends/**       ──▶  invest-dividend-service (8083)
      └── /api/notifications/**   ──▶  notification-service    (8087)
            (incluye /stream SSE, sin rate-limit)
```

`blockchain-service` (8085) no tiene ruta publica en el gateway: corre como
indexer on-chain (polling via Web3j) y publica contratos de forma asincrona,
disparado internamente por `project-service`.

Comunicacion interna service-to-service a través de endpoints `/internal/**` protegidos por red (ClusterIP en Kubernetes, sin JWT). Los servicios backend reciben la identidad como headers (`X-User-Id`, `X-User-Role`, `X-User-Permissions`) inyectados por el gateway.

### Flujo on-chain (Web3)

El `blockchain-service` es el puente entre la cadena (Ethereum/Sepolia) y el resto de la plataforma:

- **Indexer (lectura):** escanea `eth_getLogs` por rangos con checkpoints persistidos y confirmaciones, decodifica los eventos de los contratos y los traduce a topics Kafka (`investment.token_purchased`, `dividends.claimed`, `projects.round_finalized`, etc.) con `eventId = txHash:logIndex` para idempotencia.
- **Publicacion (escritura):** al aprobarse un proyecto, despliega su contrato Offering ejecutando `forge script` (Foundry) y reporta el resultado a `project-service`.

Los demas servicios reaccionan a esos eventos: `invest-dividend-service` materializa compras/dividendos y recalcula tiers; `notification-service` genera notificaciones in-app y emails.

## Servicios

| Servicio | Puerto | Descripcion | Estado |
|---------|--------|-------------|--------|
| [api-gateway](api-gateway/README.md) | 8090 | Punto de entrada unico. Valida JWT, inyecta identidad, rate limiting con Redis. | ✅ Listo |
| [auth-service](services/auth-service/README.md) | 8081 | Login y emision de JWT. Sin base de datos propia. | ✅ Listo |
| [user-service](services/user-service/README.md) | 8080 | Usuarios, roles, permisos (RBAC), KYC y tiers. | ✅ Listo |
| [project-service](services/project-service/README.md) | 8082 | CRUD de proyectos, métricas, documentos en GCS, eventos Kafka. | ✅ Listo |
| [wallet-service](services/wallet-service/README.md) | 8084 | Billeteras, movimientos, consumers Kafka idempotentes. | ✅ Listo |
| invest-dividend-service | 8083 | Compras de tokens, holdings, dividendos y recalculo de tiers a partir de eventos on-chain. Idempotente, con reconciliacion de actividad por wallet. | ✅ Listo |
| blockchain-service | 8085 | Puente on-chain: indexer de eventos (Web3j) → Kafka, publicacion de contratos Offering via Foundry (`forge`), y liquidación de trades P2P. | ✅ Listo |
| notification-service | 8087 | Notificaciones in-app + email (Resend) + stream SSE, a partir de eventos Kafka. | ✅ Listo |
| marketplace-service | 8086 | Mercado secundario P2P (Orderbook off-chain). Matching engine de órdenes de compra/venta de LKN y publicación de liquidaciones. | ✅ Listo |

## Stack comun

| Capa | Tecnologia |
|------|------------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.4 |
| Seguridad | JWT HS256 (JJWT 0.11.5) — validacion centralizada en gateway |
| Persistencia | Spring Data JPA + PostgreSQL 15 + Flyway |
| Mensajeria | Apache Kafka 7.5.0 (at-least-once + idempotencia via `eventId`) |
| Blockchain | Web3j (indexer read-only + `eth_call`) y Foundry/`forge` (deploy de contratos) sobre Ethereum/Sepolia |
| Email | Resend (SMTP) + notificaciones in-app y SSE |
| Cache / Rate limit | Redis 7 |
| Almacenamiento | Google Cloud Storage en produccion, `fake-gcs-server` en local |
| Tests | JUnit 5 + Mockito + H2 (unit) / PostgreSQL real (integration) |
| Contenedores | Docker Compose (local) / Kubernetes-GKE (produccion) |

## Levantar en local

### Requisitos

- Docker + Docker Compose
- Java 21 + Maven (solo si querés correr un servicio fuera de Docker)

### Levantar todo

```bash
docker compose up --build
```

Levanta en orden (con healthchecks): PostgreSQL → Zookeeper → Kafka → Redis → fake-gcs → fake-gcs-init (crea bucket) → user-service → auth-service → project-service → wallet-service → notification-service → invest-dividend-service → blockchain-service → api-gateway.

> **Nota Web3:** `blockchain-service` necesita un RPC (`WEB3_RPC_URL`). En local apunta por defecto a `http://host.docker.internal:8545` (Anvil corriendo en el host). Si las addresses de los contratos quedan en `0x0`, el indexer se mantiene idle sin romper el stack.

**Tiempo aproximado** del primer arranque: 3-5 min (descarga de imagenes + build Maven).

### Verificar que todo arranco

```bash
docker compose ps
```

Todos los servicios deben figurar como `(healthy)`. El servicio `fake-gcs-init` aparece como `Exited (0)` — es esperado, es un job efimero que crea el bucket y termina.

### Comandos utiles

```bash
docker compose up --build              # primera vez o tras cambiar codigo
docker compose up                       # sin cambios (usa caché)
docker compose up --build api-gateway   # rebuild solo un servicio
docker compose down                     # bajar todo (datos persisten en volumenes)
docker compose down -v                  # bajar todo y borrar datos (force re-init)
docker compose logs -f <servicio>       # ver logs de un servicio
docker compose restart <servicio>       # reiniciar un servicio sin rebuild
```

## Probar el happy path

Una vez que todo esté `(healthy)`:

1. Abri `requests.http` en la raiz del repo.
2. En **VS Code**: instala la extension [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) y hacé click en "Send Request" arriba de cada bloque.
3. En **IntelliJ**: el boton ▶ arriba de cada request lo dispara directo.
4. Con **curl**: copia las requests a mano.

El archivo cubre: healthcheck, login admin, registro de usuario, KYC, lista/creacion de proyectos, cambios de estado, wallet, deposito, movimientos, roles y permisos.

### Credenciales por defecto (solo desarrollo)

| Cuenta | Email | Password | Rol |
|--------|-------|----------|-----|
| Admin 1 | `${APP_SEED_ADMIN_EMAIL_1}` | `${APP_SEED_ADMIN_PASSWORD_1}` | ADMIN |
| Admin 2 | `${APP_SEED_ADMIN_EMAIL_2}` | `${APP_SEED_ADMIN_PASSWORD_2}` | ADMIN |

> Estos usuarios los crea el `DataSeeder` de `user-service` automaticamente al
> arrancar gracias al profile `dev` activado en `docker-compose.yml`. **No estan
> activos en produccion.**

### Credenciales de infraestructura

| Servicio | Usuario | Password |
|----------|---------|----------|
| PostgreSQL | `${POSTGRES_USER}` | `${POSTGRES_PASSWORD}` |
| JWT secret | `${JWT_SECRET}` | — |
| fake-gcs | — | sin auth (es emulador local) |

## Puertos expuestos

| Puerto | Servicio |
|--------|----------|
| 8090 | api-gateway (unico punto de entrada del front) |
| 8081 | auth-service (interno — accesible solo via gateway en produccion) |
| 8080 | user-service (interno) |
| 8082 | project-service (interno) |
| 8083 | invest-dividend-service (interno) |
| 8084 | wallet-service (interno) |
| 8085 | blockchain-service (interno — indexer + publicacion on-chain) |
| 8087 | notification-service (interno) |
| 5432 | PostgreSQL |
| 9092 | Kafka |
| 6379 | Redis |
| 4443 | fake-gcs (emulador de GCS) |

## Estructura del repositorio

```
liken-plataform-backend/
├── api-gateway/              # Spring Cloud Gateway + rate limit Redis
├── services/
│   ├── auth-service/         # Login / change-password
│   ├── user-service/         # Usuarios + RBAC + KYC + tiers
│   ├── project-service/      # Proyectos + métricas + documentos GCS
│   ├── wallet-service/       # Wallet + movimientos + consumers idempotentes
│   ├── invest-dividend-service/    # Compras + holdings + dividendos + tiers (event-sourced)
│   ├── blockchain-service/         # Indexer Web3j → Kafka + deploy de contratos (forge)
│   ├── notification-service/       # Notificaciones in-app + email (Resend) + SSE
│   └── marketplace-service/        # Pendiente (solo README)
├── contracts/                # Contratos Solidity + scripts Foundry (deploy de Offerings)
├── docs/
│   ├── adr/                  # Architecture Decision Records (ADR-0001…0022)
│   └── LISTO/                # Documentos de planificacion e historial
├── infra/
│   ├── postgres/
│   │   └── init.sql          # Crea project_db, wallet_db, etc. al iniciar Postgres
│   └── gcp-gke/              # Terraform + manifests Kubernetes + CI/CD para GKE
├── docker-compose.yml
├── implementar.md            # Hallazgos del escaneo de codigo (errores/mejoras)
└── requests.http             # Smoke test del happy path
```

## Decisiones de arquitectura (ADR)

Las decisiones de diseño estan documentadas como ADR en [`docs/adr/`](docs/adr/README.md). Los servicios referencian estos ADR (ej. la validacion centralizada de JWT es [ADR-0004](docs/adr/ADR-0004-Validacion-JWT-centralizada-en-el-gateway), el modelo de eventos Kafka es [ADR-0012](docs/adr/ADR-0012-Modelo-canonico-de-eventos-Kafka)). La integracion on-chain esta cubierta por [ADR-0017](docs/adr/ADR-0017-Modelo-de-integracion-on-chain-Web2-Web3) a [ADR-0022](docs/adr/ADR-0022-Unidades-y-precision-monetaria-on-chain-off-chain) (modelo de proyecciones, indexador, deploy con Foundry, reconciliacion por wallet, sesion y precision monetaria). El indice con el mapeo `ADR ↔ DDxxx` esta en [docs/adr/README.md](docs/adr/README.md).

