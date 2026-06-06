# 0016 - Refund con escrow en el contrato (eliminación de transferFrom sobre treasury)

## Contexto

El flujo original de `OfferingContract` enviaba el USDC directamente al `treasury` en el momento de la compra (`buy`). Si la ronda fallaba (deadline pasado sin alcanzar el soft cap), la función `refund()` devolvía el dinero a los inversores ejecutando un `safeTransferFrom(treasury, msg.sender, amount)`.

Este diseño requiere que `treasury` tenga un `approve` activo hacia el contrato por el monto total de las contribuciones. Esto genera tres problemas:

1. **Vulnerabilidad de refund**: si `treasury` gasta o revoca el `approve` antes de que los inversores ejecuten `refund()`, los fondos quedan inaccesibles. Los inversores no tienen garantía contractual de recuperar su dinero.

2. **Alerta de auditoría**: Slither reporta `arbitrary-send-erc20` (severidad alta) porque el contrato mueve fondos desde una dirección arbitraria (`treasury`) que no es `msg.sender`. Esta detección es correcta — el contrato puede drenar la cuenta de treasury si treasury aprueba más de lo necesario.

3. **Dependencia operacional off-chain**: el correcto funcionamiento de los refunds depende de una coordinación externa (que treasury mantenga el approve), lo cual no es verificable on-chain ni auditable.

En revisión con los profesores se identificó que este patrón rompe el principio de auto-custodia de los fondos de inversores, que es un requisito fundamental en cualquier contrato de inversión serio.

## Decisión

Cambiar el flujo de USDC en `OfferingContract` para que el contrato actúe como **escrow** durante la ronda:

- `buy()`: el USDC va de `msg.sender` al **contrato** (en vez de a `treasury`).
- `finalize()`: recién en la finalización exitosa, el contrato transfiere el USDC acumulado a `treasury` usando `safeTransfer`.
- `refund()`: el contrato devuelve el USDC directamente desde su propio balance usando `safeTransfer` (sin `transferFrom`, sin depender de aprobaciones externas).

El `approve` de `treasury` al contrato que existía en el `setUp` de los tests se elimina, ya que no tiene más razón de existir.

## Consecuencias

- Los inversores tienen garantía contractual de poder ejecutar `refund()` en cualquier momento si la ronda falla — el USDC está custodiado en el contrato.
- Se elimina el warning `arbitrary-send-erc20` de Slither.
- El `treasury` solo interactúa al momento de `finalize()` — no necesita mantener ningún `approve` activo.
- El test `test_BuyTransfersLKNAndUSDC` debe actualizarse: el USDC ya no va al treasury en el momento del `buy`, sino al contrato.
- Los tests de refund se simplifican: se eliminan los `usdc.mint(treasury, ...)` que compensaban artificialmente la dependencia externa.
- Se agrega un test nuevo: `test_FinalizeTransfersUSDCToTreasury`, que verifica que el USDC llega al treasury al finalizar.
- La lógica de `buy()` con cierre automático por hard cap también envía el USDC al contrato; la transferencia a treasury ocurre igualmente en ese path (dentro del mismo `buy()` cuando `totalRaised >= hardCap`).