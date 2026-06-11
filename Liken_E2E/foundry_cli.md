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

### Verificar instalación:

```bash
forge --version
cast --version
anvil --version
```

### Configurar variables

Linux:


```bash
export RPC_URL="https://eth-sepolia.g.alchemy.com/v2/TU_API_KEY"
export PRIVATE_KEY="TU_PRIVATE_KEY"
```

Windows PowerShell:

```bash
$env:RPC_URL="https://eth-sepolia.g.alchemy.com/v2/TU_API_KEY"

$env:PRIVATE_KEY="TU_PRIVATE_KEY"
```

### Verificar wallet

Obtener address desde la private key:

```bash
cast wallet address \
  --private-key $PRIVATE_KEY
```

Debería devolver:

```bash
0x....
Consultar balance ETH
cast balance \
  0xTU_WALLET \
  --rpc-url $RPC_URL
```

### Consultar balance USDC

Usando la dirección configurada:

```bash
USDC_ADDRESS= 0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
cast call \
  0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238 \
  "balanceOf(address)(uint256)" \
  0xTU_WALLET \
  --rpc-url $RPC_URL
```

Verificar que apuntás a Sepolia

```bash
cast chain-id \
  --rpc-url $RPC_URL
```

Debe devolver: `11155111`

Coincide con:
```bash
WEB3_CHAIN_ID=11155111
```

### Firmar

```bash
cast wallet sign --account dev "<nonce>"
```
