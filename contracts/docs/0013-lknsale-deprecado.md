# 0013 - LKNSale.sol deprecado en favor de OfferingContract.sol

## Contexto

LKNSale.sol fue diseñado como pasarela de compra de LKN con USDC, consultando precios al ProjectRegistry y llamando mint() en LinkenToken.

Con los cambios del ADR-011 (token global con TGE fijo, sin mint) y ADR-012 (OfferingContract con soft cap / hard cap / refund), LKNSale quedo con dos problemas estructurales:

- Dependia de mint() que ya no existe en LinkenToken.
- Su logica de precio/early bird fue absorbida por OfferingContract, que ademas agrega garantias para el inversor (soft cap, refund) y para el emisor (hard cap, escrow de LKN).

## Decision

Mover `LKNSale.sol` y `LKNSale.t.sol` a `legacy-contracts/`.

El contrato de venta primaria oficial es `OfferingContract.sol`.

## Mercado secundario (pendiente)

`LKNSale` podria haber evolucionado hacia un contrato de venta secundaria P2P (holder vende LKN a precio fijo sin pasar por el emisor). Esa funcionalidad queda pendiente de definicion con el grupo y los profesores.

Si se implementa, sera un contrato nuevo — no una resurreccion de LKNSale.

## Consecuencias

- forge test no ejecuta los tests de LKNSale salvo que se apunte explicitamente a legacy/.
- El historial de git preserva el codigo como referencia.