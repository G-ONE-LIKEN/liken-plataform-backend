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

## Topics y payloads

### `investment.token_purchased` (blockchain-service → invest, wallet, project)
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

### `marketplace.order_matched` (*sin productor aún* → wallet, project)
`eventId`, `orderId`, `projectId`, `sellerId`, `buyerId`, `tokenCount`, `price`.
Productor pendiente: marketplace-service (ver plan de mejoras §1).

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
