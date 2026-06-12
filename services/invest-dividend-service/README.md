# invest-dividend-service

Orquestador de inversiones primarias y dividendos pull bajo el modelo on-chain. Es **event-sourced**: no firma transacciones ni escribe en la cadena; materializa el estado a partir de los eventos que el `blockchain-service` publica al indexar la blockchain, y expone consultas al frontend.

## Responsabilidades

- **Registrar compras**: consume `investment.token_purchased` y guarda cada compra en `investment`. Idempotente via `processed_event.eventId`.
- **Acumular tier**: mantiene `user_investment_total.total_usdc_invested` (con lock pesimista) y recalcula el tier. Publica `user.tier_changed` cuando cruza un umbral.
- **Registrar dividendos**: consume `dividends.claimed` y guarda el reclamo en `dividend_claim`.
- **Reconciliar actividad por wallet**: la actividad on-chain puede preceder al vinculo wallet↔usuario. Las compras/dividendos que llegan sin `userId` se guardan por `walletAddress` y se "adoptan" cuando llega `user.wallet_linked` (o de forma perezosa al consultar `/me`), recomputando el total y el tier.
- **Preview de compra**: dado `projectId` + `usdcAmount`, devuelve cuantos LKN recibe el inversor al precio vigente del proyecto y si la ronda esta abierta (consulta a `project-service`).
- **Pending dividends**: lee `DividendDistributor.pendingDividends(wallet)` on-chain con `web3j` read-only (`eth_call`, no firma, no gasta gas). Ante fallo de RPC devuelve `$0`, no rompe.
- **Reporte interno de ventas primarias**: agrega las compras por rango de fechas para uso de otros servicios (no expuesto al frontend).

## Tiers

Espejo de `com.plataforma.user.model.Tier` (ambos lados deben moverse juntos):

| Tier | Umbral (USDC acumulado) |
|---|---|
| BRONZE | `$0` (default) |
| SILVER | `≥ $1000` |
| GOLD | `≥ $5000` |

## Lo que NO hace

- **Ejecutar compras**: la firma de `OfferingContract.buy()` la hace el inversor con MetaMask. Este servicio solo reconcilia con el evento resultante.
- **`depositDividends`** firmado: es una operacion admin con cuenta de plataforma, fuera del alcance (gestion de claves privadas en el backend).

## Endpoints

### Publicos (via gateway)

| Método | Ruta | Permisos | Descripcion |
|---|---|---|---|
| GET | `/api/investments/me` | autenticado | Lista paginada de las compras del usuario. Reconcilia por wallet si hace falta. |
| GET | `/api/investments/me/total` | autenticado | Total invertido + tier actual. |
| GET | `/api/investments/preview?projectId=X&usdcAmount=Y` | autenticado | LKN a recibir + flag `canInvest` + motivo. |
| GET | `/api/dividends/me` | autenticado | Historial paginado de dividendos reclamados. |
| GET | `/api/dividends/pending?wallet=0x...` | autenticado | Dividendos pendientes on-chain de una wallet. |
| GET | `/health` | publico | Healthcheck. |

### Internos (red privada, sin JWT)

| Método | Ruta | Descripcion |
|---|---|---|
| GET | `/internal/reports/primary-sales?from=YYYY-MM-DD&to=YYYY-MM-DD` | Reporte agregado de ventas primarias por rango (ambos parametros opcionales). |

## Topics Kafka

| Topic | Rol | Origen / Destino |
|---|---|---|
| `investment.token_purchased` | **Consume** | Publica `blockchain-service`. |
| `dividends.claimed` | **Consume** | Publica `blockchain-service`. |
| `user.wallet_linked` | **Consume** | Dispara la reconciliacion de actividad huérfana por wallet. |
| `user.tier_changed` | **Publica** | Lo consume `user-service` para actualizar `users.tier`. |

Idempotencia: cada evento consumido se registra en `processed_event` por `eventId` antes de aplicarse.

## Configuracion

| Env | Default | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/invest_db` | Postgres del servicio. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka. |
| `WEB3_RPC_URL` | `http://127.0.0.1:8545` | RPC para `pendingDividends`. |
| `DISTRIBUTOR_ADDRESS` | `0x0` | Address del `DividendDistributor`. En `0x0` → `pendingDividends` siempre devuelve `$0`. |
| `PROJECT_SERVICE_URL` | `http://project-service:8082` | Para el preview de compra. |
| `USER_SERVICE_URL` | `http://user-service:8080` | Lookup de identidad/wallets. |

## Modelo de datos

| Tabla | Contenido |
|---|---|
| `investment` | Una fila por compra primaria (userId nullable hasta reconciliar). |
| `dividend_claim` | Una fila por dividendo reclamado on-chain. |
| `user_investment_total` | Total acumulado + tier vigente por usuario. |
| `processed_event` | Registro de `eventId` ya procesados (idempotencia). |

## Stack

Spring Boot 3.2.4 / Java 21 / PostgreSQL + Flyway / Kafka / web3j 4.10.3.
