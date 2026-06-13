# blockchain-service

Puente Web2 ↔ Web3 de la plataforma Liken. Tiene dos mitades:

1. **Indexer (lectura):** escanea los eventos on-chain de los contratos productivos (LinkenToken, ProjectRegistry, OfferingContract por proyecto, DividendDistributor) y los publica como mensajes Kafka que los demas servicios consumen.
2. **Publicacion (escritura):** despliega el `OfferingContract` de cada proyecto aprobado ejecutando los scripts de Foundry (`forge`) y reporta el resultado a `project-service`.

## Indexer

- **Indexa logs** via polling `eth_getLogs` de bloques nuevos, con checkpoint persistente por contrato (`indexer_checkpoint`) para reanudar tras un restart sin re-procesar.
- **Respeta confirmaciones**: solo procesa hasta `head - WEB3_CONFIRMATIONS` para no indexar bloques que puedan revertirse por reorg.
- **Descubre los Offerings dinamicamente**: ademas de los contratos globales, en cada ciclo le pide a `project-service` la lista de proyectos con `offeringContractAddress` y los suma al set de contratos monitoreados.
- **Publica a Kafka** con `eventId = txHash:logIndex` para idempotencia end-to-end (los consumers también deduplican).
- **Resuelve `walletAddress → userId`** consultando `user-service`. Los eventos con `userId` null se publican igual; los consumers los reconcilian cuando se vincula la wallet.
- **Conversion de unidades**: USDC 6 dec ↔ BigDecimal escala 6; LKN 18 dec ↔ escala 8 (matchea `user_holdings.tokens_amount`).

### Topics publicados

| On-chain | → Topic Kafka |
|---|---|
| `OfferingContract.TokensPurchased` | `investment.token_purchased` |
| `OfferingContract.RoundFinalized` | `projects.round_finalized` |
| `OfferingContract.RoundFailed` | `projects.round_failed` |
| `OfferingContract.Refunded` | `wallet.refund` |
| `ProjectRegistry.StageChanged` | `projects.state_changed` |
| `DividendDistributor.DividendsDeposited` | `dividends.deposited` |
| `DividendDistributor.DividendsWithdrawn` | `dividends.claimed` |
| `LinkenToken.Transfer` (excluye mint/burn) | `token.transferred` |
| `LknMarketplace.TradeSettled` | `blockchain.trade_settled` |


## Publicacion de contratos

Cuando `project-service` aprueba un proyecto, llama a `POST /internal/publications/projects`. El servicio, de forma **asincrona**:

1. Resuelve la wallet del owner contra `user-service`.
2. Ejecuta `forge script <PUBLICATION_SCRIPT_ENTRY> --rpc-url <WEB3_RPC_URL> --broadcast` en el workspace `PUBLICATION_CONTRACTS_WORKSPACE`, pasando precios/caps/deadline como variables de entorno.
3. Parsea de la salida de `forge` el `REGISTRY_PROJECT_ID` y el `OFFERING_ADDRESS`.
4. Reporta éxito (`markPublicationSucceeded`) o fallo (`markPublicationFailed`) de vuelta a `project-service`.

> **Requisito de despliegue:** el workspace de Foundry (`/contracts` por defecto) y el binario `forge` deben estar disponibles **dentro del contenedor**, y `PUBLICATION_SIGNER_PRIVATE_KEY` + las addresses de los contratos globales deben estar configurados. En local, `docker-compose` monta `./contracts:/contracts` y el Dockerfile instala Foundry.

## Endpoints

| Método | Ruta | Tipo | Descripcion |
|---|---|---|---|
| GET | `/health` | publico | 200 OK. |
| GET | `/indexer/state` | publico | Checkpoint de cada contrato + head block y estado del RPC. |
| POST | `/internal/publications/projects` | interno | Dispara el deploy async de un Offering. Devuelve `202 Accepted`. |

`ProjectPublicationCommand` (body del POST): `projectId`, `ownerId`, `projectName`, `projectDescription`, `earlyBirdPrice`, `standardPrice`, `totalTokens`, `softCap`, `hardCap`, `expectedOpenDate`.

## Configuracion

### Indexer / RPC

| Env | Default | Descripcion |
|---|---|---|
| `WEB3_RPC_URL` | `http://127.0.0.1:8545` | RPC HTTP (Anvil local o Sepolia/Infura/Alchemy). |
| `WEB3_CHAIN_ID` | `31337` | Anvil = 31337, Sepolia = 11155111. |
| `WEB3_POLL_SECONDS` | `6` | Intervalo del polling. |
| `WEB3_MAX_BLOCK_RANGE` | `500` | Bloques por peticion `eth_getLogs`. |
| `WEB3_CONFIRMATIONS` | `1` | Bloques de delay antes de considerar un evento firme. |
| `LKN_ADDRESS` / `REGISTRY_ADDRESS` / `DISTRIBUTOR_ADDRESS` / `USDC_ADDRESS` / `MARKETPLACE_ADDRESS` | `0x0` | Addresses de los contratos globales e indexados. Si están en `0x0` el indexer correspondiente queda idle. |

### Publicacion / Foundry

| Env | Default | Descripcion |
|---|---|---|
| `PUBLICATION_CONTRACTS_WORKSPACE` | `/contracts` | Directorio con el proyecto Foundry. |
| `FORGE_COMMAND` | `forge` | Binario de Foundry. |
| `PUBLICATION_SCRIPT_ENTRY` | `script/DeployProjectOffering.s.sol:DeployProjectOffering` | Script de deploy. |
| `PUBLICATION_SIGNER_PRIVATE_KEY` | (vacio) | Clave del firmante que despliega los contratos. |
| `PLATFORM_ADMIN` / `EMISOR_ADDRESS` / `TREASURY_ADDRESS` | `0x0` | Roles on-chain del Offering. |
| `PUBLICATION_DEADLINE_ZONE_ID` | `America/Argentina/Buenos_Aires` | Zona horaria para calcular el deadline epoch. |

### Infra

| Env | Default | Descripcion |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/blockchain_db` | Postgres del servicio. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker. |
| `USER_SERVICE_URL` | `http://user-service:8080` | Lookup wallet→userId y wallet del owner. |
| `PROJECT_SERVICE_URL` | `http://project-service:8082` | Lista de Offerings a indexar + callbacks de publicacion. |

## Limitaciones conocidas

- El indexer es **single-instance**: con >1 réplica ambas escanearian los mismos bloques (la idempotencia evita duplicados, pero gasta RPC al pedo). Para escalar, particionar por contrato o usar un lock distribuido (Redis `SET NX`).
- La clave privada del firmante se pasa por variable de entorno / Secret plano. Para produccion real conviene moverla a un KMS/Secret Manager.

## Stack

Spring Boot 3.2.4 / Java 21 / PostgreSQL + Flyway / Kafka / web3j / Foundry (`forge`).
