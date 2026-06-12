# marketplace-service

Microservicio de la plataforma LIKEN responsable del mercado secundario (P2P) de tokens de proyectos de energia renovable.

## Responsabilidades

- **Publicacion de ordenes de venta** de tokens por parte de inversores.
- **Matching de ordenes**: algoritmo FIFO con price-time priority, sin matching parcial (una orden se ejecuta completa o queda `OPEN`). Matching **sincrono** al ejecutar la compra (`SELECT FOR UPDATE` sobre la orden).
- **Publicacion del evento `marketplace.order_matched`** al concretar una transaccion P2P. Lo consumen `wallet-service` (movimientos contables) y `project-service` (actualizacion de holdings).
- **Cancelacion reactiva** de ordenes si un proyecto cambia de estado (consume `projects.state_changed`).
- **Vencimiento automatico** de ordenes con TTL configurable (default 30 dias).
- **Historial de transacciones** del marketplace.

## Lo que NO hace

- **Transferencias on-chain**: el modelo actual es off-chain. La transferencia real de LKN queda como paso futuro (firma con MetaMask sobre `LinkenToken.transfer`).
- **Matching parcial**: en MVP una orden se ejecuta completa o queda `OPEN`. Post-MVP se puede agregar partial fills.
- **Bloqueo de tokens**: no se bloquean al crear la orden; se validan holdings en el momento del match (ventana de doble-venta improbable a este volumen, ADR-0014).

## Stack

| Capa | Tecnologia |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajeria | Apache Kafka |

## Endpoints

### Publicos (via gateway)

| Método | Ruta | Permiso | Descripcion |
|--------|------|---------|-------------|
| GET | `/api/marketplace/orders` | Publico | Listar ordenes activas de venta (filtrable por `projectId`) |
| GET | `/api/marketplace/orders/me` | Autenticado | Listar ordenes propias (cualquier estado) |
| POST | `/api/marketplace/orders` | Autenticado | Crear orden de venta de tokens |
| DELETE | `/api/marketplace/orders/{id}` | Owner | Cancelar una orden propia |
| POST | `/api/marketplace/orders/{id}/buy` | Autenticado | Comprar tokens de una orden |
| GET | `/api/marketplace/transactions` | Autenticado | Historial de transacciones propias |
| GET | `/health` | Publico | Healthcheck |

## Topics Kafka

**Publica:**

| Topico | Cuando | Payload |
|--------|--------|---------|
| `marketplace.order_matched` | Al concretar una transaccion P2P | `eventId`, `occurredAt`, `version`, `sellerId`, `buyerId`, `projectId`, `tokenCount`, `price`, `orderId` |

**Consume:**

| Topico | Publicado por | Para qué |
|--------|--------------|---------|
| `projects.state_changed` | blockchain-service / project-service | Cancelar ordenes activas si el proyecto se cancela, pausa o falla |

## Modelo de datos

| Tabla | Contenido |
|-------|-----------|
| `orders` | ordenes de venta/compra (status: OPEN, MATCHED, CANCELLED, EXPIRED) |
| `trades` | Transacciones completadas (un match = un trade) |
| `processed_event` | Registro de eventId ya procesados (idempotencia Kafka) |

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `SERVER_PORT` | `8086` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/marketplace_db` | URL de PostgreSQL |
| `DB_USER` | `postgres` | Usuario de la DB |
| `DB_PASSWORD` | `postgres` | Contraseña de la DB |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Brokers de Kafka |
| `PROJECT_SERVICE_URL` | `http://project-service:8082` | Para validar holdings y estado del proyecto |
| `MARKETPLACE_FEE_PERCENT` | `1.0` | Fee del marketplace (% descontado al vendedor) |
| `ORDER_TTL_DAYS` | `30` | Dias de vida de una orden antes de expirar |

## Decisiones de diseño

- **ADR-0014**: Matching engine FIFO con price-time priority, sin matching parcial.
- **ADR-0017**: Modelo off-chain con validacion de holdings; la chain es la fuente de verdad del balance pero el marketplace opera como proyeccion.
- **Fee**: Configurable via `MARKETPLACE_FEE_PERCENT`, descontado del lado vendedor, acumulado conceptualmente en la wallet de plataforma.
