# wallet-service

> **Estado: ✅ implementado**

Microservicio de la plataforma LIKEN responsable de la gestión de billeteras y movimientos de fondos de los usuarios.

## Responsabilidades

- Lazy creation de billeteras al primer acceso del usuario
- Registro de depósitos y retiros vía endpoints HTTP
- Consumo de eventos Kafka para acreditar dividendos y procesar transacciones P2P
- Idempotencia en consumers vía `external_event_id` (ver DD010)
- Concurrencia segura con `PESSIMISTIC_WRITE` sobre la fila de wallet
- Publicación de eventos `wallets.funded` y `wallets.debited` para notificaciones

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka (at-least-once + idempotencia local) |
| Seguridad | Spring Security + `GatewayHeaderAuthFilter` (DD002) |
| Tests | JUnit 5 + Mockito |

## Estructura

```
com.plataforma/
├── Application.java
├── wallet/
│   ├── model/        # Wallet, WalletMovement, MovementType
│   ├── repository/   # WalletRepository (con findByUserIdForUpdate)
│   ├── service/      # WalletService (deposit, withdraw, recordMovement)
│   ├── controller/   # WalletController
│   └── dto/
├── event/
│   ├── WalletEventPublisher.java
│   ├── consumer/     # TokenPurchasedConsumer, DividendDistributedConsumer, OrderMatchedConsumer
│   └── dto/          # eventos con campos canónicos eventId/occurredAt/version (DD010)
└── shared/
    ├── config/       # KafkaConfig, SecurityConfig
    ├── exception/    # WalletNotFoundException, InsufficientFundsException, GlobalExceptionHandler
    ├── model/        # Auditable
    └── security/     # GatewayHeaderAuthFilter (DD002)
```

## Endpoints

| Método | Ruta | Permiso | Descripción |
|--------|------|---------|-------------|
| GET | `/api/wallets/me` | Autenticado | Saldo y datos de la billetera propia (lazy creation si no existe) |
| GET | `/api/wallets/me/movements` | Autenticado | Historial de movimientos paginado |
| POST | `/api/wallets/deposit` | Autenticado | Acreditar fondos (publica `wallets.funded`) |
| POST | `/api/wallets/withdraw` | Autenticado | Debitar fondos (publica `wallets.debited`) |

## Eventos Kafka

**Publica:**

| Tópico | Cuándo |
|--------|--------|
| `wallets.funded` | Al acreditar fondos en una billetera (depósito) |
| `wallets.debited` | Al debitar fondos de una billetera (retiro) |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `dividends.distributed` | invest-dividend-service | Acreditar dividendos en la billetera del inversor |
| `investment.token_purchased` | invest-dividend-service | Debitar el costo de los tokens comprados |
| `marketplace.order_matched` | marketplace-service | Registrar movimientos P2P (vendedor + comprador) |

> Idempotencia: cada consumer pasa `event.getEventId()` a `WalletService.recordMovement`,
> que persiste el ID en la columna `external_event_id` con constraint `UNIQUE`. Una
> segunda entrega del mismo evento hace early-return sin modificar el balance.
> Detalles en DD010 + V2 migration.

## Migraciones Flyway

| Versión | Contenido |
|---------|-----------|
| V1 | Tablas `wallets` y `wallet_movements` |
| V2 | Columna `external_event_id` + UNIQUE INDEX parcial (idempotencia) |

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8084` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wallet_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |

> No usa `JWT_SECRET` — el gateway valida el JWT antes de rutear y este servicio
> confía en los headers `X-User-Id` / `X-User-Role` / `X-User-Permissions` (DD002).

## Tests

```bash
mvn test    # Unit tests con Mockito (12 tests, incluye idempotencia)
```
