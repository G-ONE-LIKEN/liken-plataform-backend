# Resumen de la arquitectura que descubri

## Componentes principales

```
Frontend (React + MetaMask)
           |
           v
      API Gateway
           |
  +----+----+----+----+
  |    |    |    |    |
User Project Wallet Invest Notification
           |
           v
   Blockchain Service
           |
           v
        Sepolia
```

## Qué hace cada servicio

### user-service

Responsable de:

* Registro
* Login
* Verificacion de email
* JWT
* Roles (ADMIN / USER)

Endpoints relevantes:

* `POST /api/auth/register/request`
* `POST /api/auth/email-verification/confirm`
* `POST /api/auth/login`

### project-service

Responsable de:

* CRUD de proyectos
* Estado del proyecto
* Precio actual
* Direccion del OfferingContract

Endpoints relevantes:

* `GET /api/projects`
* `GET /api/projects/{id}`

### wallet-service

NO maneja wallets Ethereum. Maneja una billetera interna de plataforma (quizas para firmar offering contracts):

* `GET /api/wallets/me`
* `GET /api/wallets/me/movements`
* `POST /api/wallets/deposit`
* `POST /api/wallets/withdraw`

Sirve para:

* movimientos
* reportes
* auditoria

No se vio nada relacionado con Metamask. Eso lo realiza el front-end, para back-end se emplea la herramienta foundry en terminal.

### invest-dividend-service

No compra. No firma transacciones. Hace:

* Historial de inversiones
* Total invertido
* Tier del usuario
* Dividendos
* Preview de compra

Endpoints:

* `GET /api/investments/me`
* `GET /api/investments/me/total`
* `GET /api/investments/preview`
* `GET /api/dividends/me`
* `GET /api/dividends/pending`

### blockchain-service

Es el puente blockchain ↔ backend. Escucha eventos:

* TokensPurchased
* RoundFinalized
* RoundFailed

Los convierte en eventos Kafka: `investment.token_purchased`

## Contratos

### OfferingContract

Flujo:
```
Emisor deposita LKN
      |
      v
openRound()
      |
      v
Inversores compran
      |
      v
buy(usdcAmount)
      |
      v
TokensPurchased
```

La funcion importante es:

```
buy(uint256 usdcAmount)
Flujo real de inversion
Investor Wallet
      |
      | approve()
      v
USDC Contract
      |
      | buy()
      v
OfferingContract
      |
      | emit
      v
TokensPurchased
      |
      v
Blockchain Service
      |
      v
Kafka
      |
      v
invest-dividend-service
      |
      v
GET /api/investments/me
```

## Qué puede hacer Bruno

Se pude usar Bruno para:

### Registro
```
POST /api/auth/register/request

Body:

{
  "email": "tu@mail.com",
  "password": "pass123"
}
```

### Confirmacion email

```
POST /api/auth/email-verification/confirm
{
  "email": "tu@mail.com",
  "code": "123456"
}
```

### Login

```
POST /api/auth/login
{
  "email": "tu@mail.com",
  "password": "pass123"
}
```

### Listar proyectos

```
GET /api/projects
```

### Ver detalle

```
GET /api/projects/{id}
```

## Preview

```
GET /api/investments/preview?projectId=1&usdcAmount=100
```

Obtiene Algo como:

```
{
  "offeringContractAddress": "...",
  "currentPrice": "...",
  "lknAmount": "...",
  "canInvest": true
}
```

### Ver inversiones

```
GET /api/investments/me
Authorization: Bearer JWT
```

### Ver total

```
GET /api/investments/me/total
Authorization: Bearer JWT
```

### Ver dividendos

```
GET /api/dividends/me
Authorization: Bearer JWT
```

### Dividendos pendientes

```
GET /api/dividends/pending?wallet=0x...
```

## Configuracion minima de Foundry

### Verificar instalacion:

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

Deberia devolver:

```bash
0x....
Consultar balance ETH
cast balance \
  0xTU_WALLET \
  --rpc-url $RPC_URL
```

### Consultar balance USDC

Usando la direccion configurada:

```bash
USDC_ADDRESS= 0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
cast call \
  0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238 \
  "balanceOf(address)(uint256)" \
  0xTU_WALLET \
  --rpc-url $RPC_URL
```

Verificar que apuntas a Sepolia

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

## Flujo tentativo de inversion

1. Obtener proyecto

> GET /api/projects

2. Obtener preview

> GET /api/investments/preview?projectId=1&usdcAmount=100

Guardar: `offeringContractAddress`

3. Aprobar USDC, por ejemplo: 1 USDC: 1000000 (6 decimales)

```bash
cast send \
  $USDC_ADDRESS \
  "approve(address,uint256)" \
  $OFFERING_ADDRESS \
  100000000 \
  --private-key $PRIVATE_KEY \
  --rpc-url $RPC_URL
```

4. Comprar

```bash
cast send \
  $OFFERING_ADDRESS \
  "buy(uint256)" \
  100000000 \
  --private-key $PRIVATE_KEY \
  --rpc-url $RPC_URL
```

5. Esperar indexacion. Segun .env:

```bash
WEB3_POLL_SECONDS=6
WEB3_CONFIRMATIONS=2
```

Esperaria entre: 15 y 60 segundos.

6. Consultar resultado:

> GET /api/investments/me

y

> GET /api/investments/me/total

Si aparecen los datos, quedo validado el flujo completo de:

```bash
Foundry
  ↓
Sepolia
  ↓
OfferingContract
  ↓
Blockchain Service
  ↓
Kafka
  ↓
invest-dividend-service
  ↓
REST API
```