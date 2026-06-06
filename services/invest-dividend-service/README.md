# invest-dividend-service

Orquestador de inversiones primarias y dividendos pull bajo el modelo on-chain.

## Responsabilidades

- **Registrar compras**: consume `investment.token_purchased` que publica el
  Blockchain Service y guarda el registro en `investment`.
- **Acumular tier**: mantiene `user_investment_total.totalUsdcInvested` y
  recalcula el tier (BRONZE / SILVER / GOLD). Publica `user.tier_changed`
  cuando cruza un umbral.
- **Registrar dividendos**: consume `dividends.claimed` y guarda el reclamo en
  `dividend_claim`.
- **Preview de compra**: dado un `projectId` + `usdcAmount`, devuelve cuántos
  LKN recibe el inversor al precio vigente del proyecto (consulta a
  `project-service`).
- **Pending dividends**: lee `DividendDistributor.pendingDividends(wallet)`
  on-chain con `web3j` read-only (`eth_call`, no firma, no gasta gas).

## Lo que NO hace (todavía)

- **Ejecutar compras**: la firma `OfferingContract.buy()` la hace el inversor
  con MetaMask. Este servicio solo reconcilia con el evento.
- **`depositDividends`** firmado: queda para una operación admin con cuenta de
  plataforma (fuera del alcance — gestión de claves privadas en el backend).

## Endpoints

| Método | Ruta | Permisos | Descripción |
|---|---|---|---|
| GET | `/api/investments/me` | autenticado | Lista las compras del usuario. |
| GET | `/api/investments/me/total` | autenticado | Total invertido + tier actual. |
| GET | `/api/investments/preview?projectId=X&usdcAmount=Y` | autenticado | Cuántos LKN se reciben + flag `canInvest`. |
| GET | `/api/dividends/me` | autenticado | Historial de dividendos reclamados. |
| GET | `/api/dividends/pending?wallet=0x...` | autenticado | Dividendos pendientes on-chain. |
| GET | `/health` | público | Healthcheck. |

## Topics Kafka

| Topic | Rol |
|---|---|
| `investment.token_purchased` | **Consume**. Publica el Blockchain Service. |
| `dividends.claimed` | **Consume**. Publica el Blockchain Service. |
| `user.tier_changed` | **Publica**. Lo consume `user-service`. |

## Configuración

| Env | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/invest_db` | Postgres del servicio. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka. |
| `WEB3_RPC_URL` | `http://127.0.0.1:8545` | RPC para `pendingDividends`. |
| `DISTRIBUTOR_ADDRESS` | `0x0` | Address del DividendDistributor. Si queda en 0x0, `pendingDividends` siempre devuelve $0. |
| `PROJECT_SERVICE_URL` | `http://project-service:8082` | Para preview. |
| `USER_SERVICE_URL` | `http://user-service:8080` | (Reservado, no usado todavía.) |

## Stack

Spring Boot 3.2.4 / Java 21 / PostgreSQL + Flyway / Kafka / web3j 4.10.3.
