# marketplace-service

Microservicio de la plataforma LIKEN responsable del mercado secundario (P2P) de tokens de proyectos de energia renovable.

## Responsabilidades

- **Publicacion de ordenes de venta** de tokens por parte de inversores.
- **Matching de ordenes**: algoritmo FIFO con price-time priority, sin matching parcial (una orden se ejecuta completa o queda `OPEN`). Matching **sincrono** al ejecutar la compra (`SELECT FOR UPDATE` sobre la orden).
- **Inicio de la liquidación on-chain**: al matchear una orden, se cambia su estado a `PENDING_SETTLEMENT` y se publica el evento `marketplace.order_matched` para que el servicio de blockchain realice la liquidación (swap atómico LKN/USDC) en la testnet.
- **Confirmación definitiva de la transacción**: consume `marketplace.trade_settled` desde Kafka (emitido por el indexador on-chain), transiciona el estado de la orden de `PENDING_SETTLEMENT` a `MATCHED`, persiste el `Trade` en la base de datos y publica el evento definitivo `marketplace.trade_settled` para otros servicios.
- **Cancelacion reactiva** de ordenes si un proyecto cambia de estado (consume `projects.state_changed`).
- **Vencimiento automatico** de ordenes con TTL configurable (default 30 dias).
- **Historial de transacciones** del marketplace.

## Lo que NO hace

- **Firma delegada de gas**: los usuarios compradores/vendedores firman la aprobación (`approve`) de sus respectivos tokens LKN/USDC en MetaMask hacia el contrato mediador, pero es la plataforma (el settler admin) quien firma y paga el gas de la transacción `settleTrade` on-chain.
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
| `marketplace.order_matched` | Al matchear una orden (inicia liquidación en blockchain) | `eventId`, `occurredAt`, `version`, `sellerId`, `buyerId`, `projectId`, `tokenCount`, `price`, `orderId` |
| `marketplace.trade_settled` | Al confirmarse la liquidación on-chain | `eventId`, `occurredAt`, `version`, `sellerId`, `buyerId`, `projectId`, `tokenCount`, `price`, `orderId`, `txHash` |

**Consume:**

| Topico | Publicado por | Para qué |
|--------|--------------|---------|
| `projects.state_changed` | blockchain-service / project-service | Cancelar ordenes activas si el proyecto se cancela, pausa o falla |
| `blockchain.trade_settled` | blockchain-service | Finalizar la orden a MATCHED y guardar el Trade |
| `blockchain.trade_failed` | blockchain-service | Revertir la orden de PENDING_SETTLEMENT a OPEN si falla on-chain |

## Modelo de datos

| Tabla | Contenido |
|-------|-----------|
| `orders` | ordenes de venta/compra (status: OPEN, PENDING_SETTLEMENT, MATCHED, CANCELLED, EXPIRED) |
| `trades` | Transacciones completadas (un match = un trade persistido tras confirmación on-chain) |
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
