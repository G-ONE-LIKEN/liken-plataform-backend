# Esquemas de eventos Kafka

> Complemento de ADR-0012 (modelo canónico) y ADR-0024 (garantías de entrega).
> Todos los eventos llevan el sobre común y los consumers deben ser
> **idempotentes por `eventId`** (at-least-once garantizado).

## Sobre común

| Campo | Tipo | Descripción |
|---|---|---|
| `eventId` | string | Único. On-chain: `<txHash>:<logIndex>`. Sintéticos: prefijo descriptivo (`expired:<address>`). |
| `version` | int | Versión del esquema (hoy 1). |
| `occurredAt` | string ISO | Momento de publicación. |

Eventos derivados de la chain agregan: `txHash`, `blockNumber`, `logIndex`.

## Catálogo de topics

Mapa completo productor → consumidores (verificado contra el código). Los topics
`*.DLT` se crean automáticamente por dominio de consumo (ADR-0024) y no se listan.

| Topic | Productor | Consumidores |
|---|---|---|
| `investment.token_purchased` | blockchain-service | invest, wallet, project, notification |
| `projects.round_finalized` | blockchain-service | project |
| `projects.round_failed` | blockchain-service | project |
| `projects.state_changed` | project-service, blockchain-service | marketplace |
| `projects.created` | project-service | — |
| `projects.pending_approval` | project-service | notification |
| `projects.approved` | project-service | notification |
| `projects.rejected` | project-service | notification |
| `projects.metrics_updated` | project-service | — |
| `token.transferred` | blockchain-service | project |
| `wallet.refund` | blockchain-service | wallet |
| `wallet.credited` | wallet-service | notification |
| `wallet.debited` | wallet-service | notification |
| `oracle.energy_reading` | oracle-service | invest |
| `dividends.deposit_requested` | invest-dividend-service | blockchain-service |
| `dividends.deposited` | blockchain-service | invest, notification |
| `dividends.deposit_failed` | blockchain-service | invest |
| `dividends.payout_batch_requested` | invest-dividend-service | blockchain-service |
| `dividends.payout_batch_failed` | blockchain-service | invest |
| `dividends.paid` | blockchain-service | invest |
| `dividends.paid_failed` | blockchain-service | invest |
| `dividends.claimed` | blockchain-service | invest, wallet, notification |
| `marketplace.order_matched` | marketplace-service | blockchain-service |
| `marketplace.trade_settled` | marketplace-service | wallet, project |
| `blockchain.trade_settled` | blockchain-service | marketplace |
| `blockchain.trade_failed` | blockchain-service | marketplace |
| `user.registered` | user-service | notification |
| `user.developer_registered` | user-service | notification |
| `user.developer_status_changed` | user-service | notification |
| `user.wallet_linked` | user-service | wallet, invest, project |
| `user.tier_changed` | invest-dividend-service | user-service |
| `user.context_invalidated` | user-service | (gateway cache) |

## Topics y payloads

### `investment.token_purchased` (blockchain-service → invest, wallet, project, notification)
| Campo | Tipo | Nota |
|---|---|---|
| `walletAddress` | string | Comprador on-chain. |
| `userId` | long? | Null si la wallet no está vinculada (ADR-0020). |
| `offeringContractAddress` | string | Para resolver `projectId`. |
| `usdcAmount` | decimal(6) | Pagado. |
| `lknAmount` | decimal(8) | Recibido. |

### `projects.round_finalized` / `projects.round_failed` (blockchain-service → project)
| Campo | Tipo | Nota |
|---|---|---|
| `offeringContractAddress` | string | Clave de reconciliación. |
| `totalRaised` | decimal(6) | |
| `softCap` | decimal(6) | Solo en `round_failed`. |
| `lknSold` | decimal(8) | Solo en `round_finalized`. |
| `synthetic` | bool? | `true` si lo emitió `RoundExpirationMonitor` (ronda vencida detectada por eth_call, sin evento on-chain todavía). |

### `wallet.refund` (blockchain-service → wallet)
`walletAddress`, `userId?`, `usdcAmount` (decimal 6), `offeringContractAddress`.

### `dividends.deposited` (blockchain-service → notification)
`depositor` (address), `amount` (decimal 6).

### `dividends.claimed` (blockchain-service → invest, wallet)
`walletAddress`, `userId?`, `amount` (decimal 6).

### `token.transferred` (blockchain-service → project)
`from`, `to` (addresses), `amount` (decimal 8). Mint/burn excluidos.

### `projects.state_changed` (blockchain-service y project-service → marketplace*, notification)
`registryProjectId` (long), `newStage` (`FUNDING|ACTIVE|PAUSED`) — variante on-chain.
La variante off-chain de project-service lleva `projectId`, `oldState`, `newState`.

### `user.wallet_linked` (user-service → wallet, invest, project)
`userId` (long), `walletAddress` (string). Dispara reconciliación de actividad huérfana.

### `user.tier_changed` (invest → user-service)
`userId`, `oldTier`, `newTier`.

### `user.registered` / `user.developer_registered` / `user.developer_status_changed` (user-service → notification)
`userId`, más datos del usuario/developer según el evento. Disparan emails de bienvenida y avisos de cambio de estado de developer.

### `oracle.energy_reading` (oracle-service → invest)
| Campo | Tipo | Nota |
|---|---|---|
| `projectId` | long | Proyecto medido. |
| `readingTimestamp` | string ISO | Momento de la lectura simulada. |
| `energyKWh` | decimal | Energía generada en el intervalo. |

`invest-dividend-service` acumula esta energía por proyecto (`ProjectEnergyAccumulator`) y la usa como base para los pagos de dividendos.

### Flujo de dividendos automáticos (invest ↔ blockchain)

Reparto on-chain de dividendos por la energía generada. El holder no firma nada: los USDC llegan a su wallet por transferencia directa del signer admin.

| Topic | Productor → Consumidor | Payload |
|---|---|---|
| `dividends.deposit_requested` | invest → blockchain | `projectId`, `amountUsdc` |
| `dividends.deposited` | blockchain → invest, notification | `depositor` (address), `amount` (decimal 6) |
| `dividends.deposit_failed` | blockchain → invest | motivo de la falla |
| `dividends.payout_batch_requested` | invest → blockchain | `batchId`, `projectId`, `payouts[]` (`userId`, `walletAddress`, `amount`, `payoutEventId`) |
| `dividends.paid` | blockchain → invest | confirmación de pago individual (`txHash`, `walletAddress`, `amount`) |
| `dividends.paid_failed` / `dividends.payout_batch_failed` | blockchain → invest | falla de un pago / del batch completo |
| `dividends.claimed` | blockchain → invest, wallet, notification | `walletAddress`, `userId?`, `amount` (decimal 6) — pool legacy `DividendDistributor` (pull) |

### Marketplace P2P (liquidación on-chain)

| Topic | Productor → Consumidor | Nota |
|---|---|---|
| `marketplace.order_matched` | marketplace → blockchain | inicia la liquidación on-chain del swap LKN/USDC |
| `blockchain.trade_settled` | blockchain → marketplace | el swap se liquidó: orden pasa a MATCHED, se persiste el Trade |
| `blockchain.trade_failed` | blockchain → marketplace | el swap falló: orden vuelve de PENDING_SETTLEMENT a OPEN |
| `marketplace.trade_settled` | marketplace → wallet, project | trade confirmado: actualiza movimientos de wallet y holdings |

Payload de `marketplace.order_matched` / `marketplace.trade_settled`: `orderId`, `projectId`, `sellerId`, `buyerId`, `tokenCount`, `price` (+ `txHash` en el settled).

### `wallet.credited` / `wallet.debited` (wallet-service → notification)
`userId`, `amount`, `movementType`, `description`. Disparan notificaciones de movimiento de saldo.

## Garantías (ADR-0024)

- **Producción**: el indexer publica con `send().get()` antes de avanzar el
  checkpoint → at-least-once.
- **Consumo** (invest, wallet, project): 3 reintentos (2s) → `<topic>.DLT`
  (partición 0) con headers de diagnóstico. Reproceso: consumir el DLT y
  republicar al topic original; la idempotencia absorbe lo ya procesado.
- **notification-service**: best-effort deliberado (sin DLT) — un email
  fallido no debe frenar el pipeline.

## Cómo inspeccionar un DLT

```bash
kubectl -n app exec deploy/kafka -- kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic investment.token_purchased.DLT \
  --from-beginning --property print.headers=true
```
