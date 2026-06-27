# Scripts operativos de contratos

Utilidades para mantener el ambiente de testing en Sepolia: fondear testers con
LKN y leer balances/allowances rapido sin tener que abrir Etherscan.

Los scripts reusan el `.env` del backend (`liken-plataform-backend/.env`), asi
que no hay que duplicar API keys ni private keys. Las variables que toma son:

- `WEB3_RPC_URL` — endpoint Alchemy / Infura de Sepolia.
- `PUBLICATION_SIGNER_PRIVATE_KEY` — wallet admin que firma las transferencias.
- `MARKETPLACE_ADDRESS`, `LKN_ADDRESS`, `USDC_ADDRESS` — contratos deployados.

> Importante: NUNCA pongas estas claves hardcoded en el codigo. Si alguien las
> filtra al historial de git hay que rotar la wallet y revocar la API key.

## Setup

```bash
cd contracts/ops
npm install
```

## Uso

Chequear balances y allowances de un par seller/buyer:

```bash
npm run check-balances -- 0xSellerAddress 0xBuyerAddress
```

Mandar LKN a una wallet de testing desde la wallet admin:

```bash
npm run transfer-lkn -- 0xDestinationAddress 100
```

El monto se expresa en LKN (no en wei). Internamente se multiplica por 10^18.
