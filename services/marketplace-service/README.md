# marketplace-service

Microservicio de la plataforma LIKEN responsable del mercado secundario (P2P) de tokens de proyectos de energía renovable.

## Responsabilidades

- **Publicación de órdenes de venta** de tokens por parte de inversores.
- **Matching de órdenes**: algoritmo FIFO con price-time priority, sin matching parcial (una orden se ejecuta completa o queda `OPEN`). Matching **síncrono** al ejecutar la compra (`SELECT FOR UPDATE` sobre la orden).
- **Publicación del evento `marketplace.order_matched`** al concretar una transacción P2P. Lo consumen `wallet-service` (movimientos contables) y `project-service` (actualización de holdings).
- **Cancelación reactiva** de órdenes si un proyecto cambia de estado (consume `projects.state_changed`).
- **Vencimiento automático** de órdenes con TTL configurable (default 30 días).
- **Historial de transacciones** del marketplace.

## Lo que NO hace

- **Transferencias on-chain**: el modelo actual es off-chain. La transferencia real de LKN queda como paso futuro (firma con MetaMask sobre `LinkenToken.transfer`).
- **Matching parcial**: en MVP una orden se ejecuta completa o queda `OPEN`. Post-MVP se puede agregar partial fills.
- **Bloqueo de tokens**: no se bloquean al crear la orden; se validan holdings en el momento del match (ventana de doble-venta improbable a este volumen, ADR-0014).

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka |

## Endpoints

### Públicos (vía gateway)

| Método | Ruta | Permiso | Descripción |
|--------|------|---------|-------------|
| GET | `/api/marketplace/orders` | Público | Listar órdenes activas de venta (filtrable por `projectId`) |
| GET | `/api/marketplace/orders/me` | Autenticado | Listar órdenes propias (cualquier estado) |
| POST | `/api/marketplace/orders` | Autenticado | Crear orden de venta de tokens |
| DELETE | `/api/marketplace/orders/{id}` | Owner | Cancelar una orden propia |
| POST | `/api/marketplace/orders/{id}/buy` | Autenticado | Comprar tokens de una orden |
| GET | `/api/marketplace/transactions` | Autenticado | Historial de transacciones propias |
| GET | `/health` | Público | Healthcheck |

## Topics Kafka

**Publica:**

| Tópico | Cuándo | Payload |
|--------|--------|---------|
| `marketplace.order_matched` | Al concretar una transacción P2P | `eventId`, `occurredAt`, `version`, `sellerId`, `buyerId`, `projectId`, `tokenCount`, `price`, `orderId` |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `projects.state_changed` | blockchain-service / project-service | Cancelar órdenes activas si el proyecto se cancela, pausa o falla |

## Modelo de datos

| Tabla | Contenido |
|-------|-----------|
| `orders` | Órdenes de venta/compra (status: OPEN, MATCHED, CANCELLED, EXPIRED) |
| `trades` | Transacciones completadas (un match = un trade) |
| `processed_event` | Registro de eventId ya procesados (idempotencia Kafka) |

## Configuración

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SERVER_PORT` | `8086` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/marketplace_db` | URL de PostgreSQL |
| `DB_USER` | `postgres` | Usuario de la DB |
| `DB_PASSWORD` | `postgres` | Contraseña de la DB |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Brokers de Kafka |
| `PROJECT_SERVICE_URL` | `http://project-service:8082` | Para validar holdings y estado del proyecto |
| `MARKETPLACE_FEE_PERCENT` | `1.0` | Fee del marketplace (% descontado al vendedor) |
| `ORDER_TTL_DAYS` | `30` | Días de vida de una orden antes de expirar |

## Decisiones de diseño

- **ADR-0014**: Matching engine FIFO con price-time priority, sin matching parcial.
- **ADR-0017**: Modelo off-chain con validación de holdings; la chain es la fuente de verdad del balance pero el marketplace opera como proyección.
- **Fee**: Configurable vía `MARKETPLACE_FEE_PERCENT`, descontado del lado vendedor, acumulado conceptualmente en la wallet de plataforma.
