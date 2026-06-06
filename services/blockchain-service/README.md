# blockchain-service

Puente Web2 ↔ Web3 de la plataforma Liken. Indexa eventos on-chain de los
contratos productivos (LinkenToken, ProjectRegistry, OfferingContract,
DividendDistributor) y los publica como mensajes Kafka que los demás servicios
consumen.

## Responsabilidades

- **Indexar logs** via `eth_getLogs` polling de bloques nuevos, con checkpoint
  persistente por contrato para reanudar tras restart sin re-procesar.
- **Publicar a Kafka** con `eventId = txHash:logIndex` para idempotencia
  end-to-end (los consumers ya hacen idempotencia por `externalEventId`).
- **Resolver `walletAddress → userId`** consultando `user-service`
  (`/internal/users/by-wallet/{address}`) — eventos con userId null se publican
  igualmente y los consumers los descartan hasta vínculo.
- **Conversión de unidades**: USDC 6 dec ↔ BigDecimal escala 6;
  LKN 18 dec ↔ escala 8 (matchea `user_holdings.tokens_amount`).

## Topics publicados (alineados con `docs/implementar-con-blockchain.md`)

| On-chain | → Topic Kafka | Consumer |
|---|---|---|
| `OfferingContract.TokensPurchased` | `investment.token_purchased` | wallet-service, project-service |
| `OfferingContract.RoundFinalized` | `projects.round_finalized` | project-service |
| `OfferingContract.RoundFailed` | `projects.round_failed` | project-service |
| `OfferingContract.Refunded` | `wallet.refund` | wallet-service |
| `ProjectRegistry.StageChanged` | `projects.state_changed` | project-service |
| `DividendDistributor.DividendsDeposited` | `dividends.deposited` | invest-dividend-service |
| `DividendDistributor.DividendsWithdrawn` | `dividends.claimed` | wallet-service |
| `LinkenToken.Transfer` (no mint/burn) | `token.transferred` | analítica |

## Configuración

Variables de entorno principales:

| Env | Default | Descripción |
|---|---|---|
| `WEB3_RPC_URL` | `http://127.0.0.1:8545` | RPC HTTP del nodo (Anvil local o Sepolia/Infura/Alchemy). |
| `WEB3_CHAIN_ID` | `31337` | Anvil = 31337, Sepolia = 11155111. |
| `WEB3_POLL_SECONDS` | `6` | Intervalo del polling. |
| `WEB3_MAX_BLOCK_RANGE` | `500` | Bloques por petición `eth_getLogs`. |
| `WEB3_CONFIRMATIONS` | `1` | Bloques de delay antes de considerar firme. |
| `LKN_ADDRESS` | 0x0 | LinkenToken global. |
| `REGISTRY_ADDRESS` | 0x0 | ProjectRegistry global. |
| `DISTRIBUTOR_ADDRESS` | 0x0 | DividendDistributor global. |
| `USDC_ADDRESS` | 0x0 | USDC ERC-20 (en Sepolia: el oficial de Circle). |
| `USER_SERVICE_URL` | `http://user-service:8080` | Para el lookup wallet→userId. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/blockchain_db` | Postgres del servicio. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker. |

Si las addresses están en `0x0` (default), el indexer no escanea — útil para
arrancar el stack sin esperar a que los contratos estén desplegados.

## Endpoints

- `GET /health` → 200 OK.
- `GET /indexer/state` → checkpoint actual de cada contrato + head block del RPC.

## Limitaciones conocidas

- **OfferingContracts per-proyecto** todavía no se indexan automáticamente.
  Para escucharlos hay que cargarlos dinámicamente desde `project-service`
  (proyectos con `offeringContractAddress != null`). Pendiente — la integración
  inicial cubre los contratos globales y los eventos del Offering aparecerán
  cuando se complete esa parte.
- **Operaciones admin firmadas** (registerProject, grantRole, depositDividends,
  deploy de Offering por proyecto) **no** las hace este servicio todavía. Hoy
  son tareas operativas que se ejecutan con Foundry / scripts.
- El indexer es **single-instance**: si lo escalás a >1 réplica, las dos
  procesarían los mismos bloques (la idempotencia en `published_event` y en los
  consumers evita duplicados, pero gastás RPC al pedo). Para distribuir,
  particionar por contrato o usar un lock distribuido (Redis SET NX).
