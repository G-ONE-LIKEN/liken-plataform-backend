# Foundry CLI

Documento que especifica como realizar inversiones desde un CLI sin usar el front-end.

Prerequisitos:
* Tener instalada la herramienta foundry.
* Tener configurada la herramienta foundry con los parametros de tu wallet.


> [USDC SEPOLIA] USDC_ADDRESS=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238

## Aprobar USDC

```bash
cast send \
  $USDC_ADDRESS \
  "approve(address,uint256)" \
  $OFFERING_ADDRESS \
  100000000 \
  --private-key $TU_PRIVATE_KEY \
  --rpc-url $WEB3_RPC_URL
```

## Comprar

```bash
cast send \
  $OFFERING_ADDRESS \
  "buy(uint256)" \
  100000000 \
  --private-key $TU_PRIVATE_KEY \
  --rpc-url $WEB3_RPC_URL
```