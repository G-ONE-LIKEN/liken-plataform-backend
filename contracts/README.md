# Linken (LKN) — Plataforma de Tokenización de Proyectos Energéticos

Monorepo con smart contracts en Solidity (Foundry) + frontend web para interactuar con la plataforma desde el navegador.

---

## Índice

1. [Arquitectura general](#arquitectura-general)
2. [Estructura del monorepo](#estructura-del-monorepo)
3. [Smart Contracts](#smart-contracts)
4. [Flujo del sistema](#flujo-del-sistema)
5. [Prerrequisitos](#prerrequisitos)
6. [Setup — Contratos](#setup--contratos)
7. [Tests y coverage](#tests-y-coverage)
8. [Análisis estático con Slither](#análisis-estático-con-slither)
9. [Setup — Frontend](#setup--frontend)
10. [Variables de entorno](#variables-de-entorno)
11. [Deploy](#deploy)
12. [Seguridad](#seguridad)
13. [Decisiones de arquitectura (ADRs)](#decisiones-de-arquitectura-adrs)
14. [Roadmap](#roadmap)

---

## Arquitectura general

Linken es una plataforma de tokenización de proyectos de generación de energía renovable. Permite a inversores adquirir participaciones fraccionadas en parques solares, eólicos y similares mediante tokens ERC-20, y recibir dividendos proporcionales a los ingresos generados por cada proyecto.

### Contratos productivos

| Contrato | Responsabilidad |
|---|---|
| `LinkenToken.sol` | Token ERC-20 global LKN. Supply fijo emitido en el TGE, sin mint posterior. |
| `ProjectRegistry.sol` | Registro de proyectos con ciclo de vida (FUNDING → ACTIVE → PAUSED) y precios por etapa. |
| `OfferingContract.sol` | Venta primaria de LKN con precio fijo, soft cap, hard cap y refund. Activa el proyecto en el Registry al finalizar. |
| `DividendDistributor.sol` | Recibe USDC y los distribuye proporcionalmente entre holders de LKN usando el patrón pull payment. |

### Contratos deprecados (en `src/legacy/`)

| Contrato | Motivo |
|---|---|
| `LinkenToken.sol` (v1) | Reemplazado — tenía mint ilimitado y supply infinito. |
| `ProjectToken.sol` | Reemplazado — el modelo de subtokens por proyecto fue simplificado a token global LKN. |
| `ProjectFactory.sol` | Reemplazado por `ProjectRegistry.sol`. |
| `LKNSale.sol` | Reemplazado por `OfferingContract.sol`. |

---

## Estructura del monorepo

```
linken/
├── contracts/
│   ├── src/
│   │   ├── LinkenToken.sol
│   │   ├── ProjectRegistry.sol
│   │   ├── OfferingContract.sol
│   │   ├── DividendDistributor.sol
│   │   ├── interfaces/
│   │   │   └── IDividendDistributor.sol
│   │   └── legacy/
│   │       ├── ProjectToken.sol
│   │       ├── ProjectFactory.sol
│   │       └── LKNSale.sol
│   ├── test/
│   │   ├── LinkenToken.t.sol
│   │   ├── ProjectRegistry.t.sol
│   │   ├── OfferingContract.t.sol
│   │   ├── DividendDistributor.t.sol
│   │   ├── Integration.t.sol
│   │   └── legacy/
│   ├── script/
│   │   ├── DeployAll.s.sol
│   │   └── legacy/
│   ├── foundry.toml
│   └── remappings.txt
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   └── lib/
│   └── package.json
├── docs/
│   ├── 0001-monorepo.md
│   ├── 0002-openzeppelin-v5.md
│   ├── 0003-solidity-0.8.24.md
│   ├── 0004-stack-frontend.md
│   ├── 0005-access-control-roles.md
│   ├── 0006-creator-role-factory.md
│   ├── 0007-factory-pattern-project-tokens.md
│   ├── 0008-pull-payment-dividends.md
│   ├── 0009-linken-deprecado.md
│   ├── 0010-simplificacion-token-global-lkn.md  [DEPRECADO]
│   ├── 0011-token-global-tge-fijo.md
│   ├── 0012-offering-contract-tge-flow.md
│   ├── 0013-lknsale-deprecado.md
│   └── 0014-offering-registry-integration.md
└── README.md
```

---

## Smart Contracts

### LinkenToken.sol

Token ERC-20 global de la plataforma.

- **TGE (Token Generation Event)**: el supply se define en el constructor y se emite una única vez al emisor (SPE dueño del parque). No hay mint posterior.
- **Burn libre**: cualquier holder puede quemar sus tokens, reduciendo el supply circulante.
- **AccessControl**: roles separados para administración (`DEFAULT_ADMIN_ROLE`) y pausa (`PAUSER_ROLE`).
- **ReentrancyGuard**: protege burn contra ataques de reentrada.
- **DividendDistributor hook**: notifica al distributor en cada transferencia entre holders para mantener las correcciones de dividendos actualizadas.

```solidity
constructor(address platformAdmin, address tgeRecipient, uint256 tgeSupply)
```

---

### ProjectRegistry.sol

Registro central de proyectos energéticos.

- **Ciclo de vida**: `FUNDING → ACTIVE → PAUSED`
- **Precios por etapa**: `earlyBirdPrice` (FUNDING) y `standardPrice` (ACTIVE), ambos en USDC/LKN con 6 decimales.
- **CREATOR_ROLE**: solo desarrolladores aprobados pueden registrar proyectos.
- **OFFERING_ROLE**: solo `OfferingContract` autorizados pueden activar proyectos automáticamente.

```
earlyBirdPrice < standardPrice  (validado en el contrato)
```

---

### OfferingContract.sol

Venta primaria de LKN a precio fijo con garantías para el inversor.

- **Escrow de LKN**: el emisor deposita los tokens antes de abrir la ronda.
- **Soft cap**: si no se alcanza antes del deadline, los inversores pueden pedir refund.
- **Hard cap**: al alcanzarse, la ronda cierra automáticamente.
- **Activación automática**: al finalizar exitosamente, activa el proyecto en el `ProjectRegistry` (FUNDING → ACTIVE).
- **Refund (pull payment)**: si la ronda falla, cada inversor retira su USDC individualmente.

```
lknAmount = (usdcAmount * 1e18) / tokenPrice
```

---

### DividendDistributor.sol

Distribuye USDC entre holders de LKN usando el algoritmo *dividends per share*.

- **Patrón pull**: la plataforma deposita USDC una vez; cada holder retira cuando quiere.
- **Sin loops**: no itera sobre holders — escala a cualquier cantidad de inversores.
- **Corrección por transferencia**: cuando un holder transfiere tokens, sus derechos adquiridos se preservan.
- **DEPOSITOR_ROLE**: solo la plataforma puede depositar dividendos.

```
magnifiedDPShare += (depositado * 2^128) / totalSupply
pendiente(user)   = (balance(user) * magnifiedDPShare + corrección(user)) / 2^128
```

---

## Flujo del sistema

```
TGE
  Emisor despliega LinkenToken → recibe N LKN

SETUP DE RONDA
  Admin registra proyecto en ProjectRegistry (stage=FUNDING, precios)
  Admin despliega OfferingContract (precio, soft cap, hard cap, deadline)
  Admin otorga OFFERING_ROLE al OfferingContract en el Registry
  Emisor deposita LKN en OfferingContract (escrow)
  Emisor abre la ronda

RONDA ABIERTA (stage=FUNDING — precio early bird)
  Inversor aprueba USDC al OfferingContract
  Inversor llama buy(usdcAmount)
  → USDC va al treasury
  → LKN van al inversor

CIERRE EXITOSO (soft cap alcanzado)
  Por hard cap: cierre automático en buy()
  Por finalize(): emisor cierra manualmente
  → LKN no vendidos devueltos al emisor
  → ProjectRegistry actualiza stage=ACTIVE automáticamente

POST-APERTURA (stage=ACTIVE — precio estándar)
  Frontend muestra el nuevo precio
  La plataforma conecta DividendDistributor al token

DIVIDENDOS
  Plataforma deposita USDC en DividendDistributor
  Cada holder llama claimDividends() cuando quiere

RONDA FALLIDA (deadline sin soft cap)
  Cada inversor llama refund()
  → Treasury devuelve USDC individualmente
  → Proyecto permanece en FUNDING para nueva ronda
```

---

## Prerrequisitos

### Node.js (frontend)

```bash
# Arch Linux
sudo pacman -S nodejs npm

# Ubuntu / Debian
sudo apt install nodejs npm

# macOS
brew install node

# Verificar
node --version   # >= 18
npm --version
```

### Foundry (contratos)

```bash
curl -L https://foundry.paradigm.xyz | bash
source ~/.bashrc   # o ~/.zshrc
foundryup

forge --version
cast --version
```

### Slither (análisis estático, opcional)

```bash
pip install slither-analyzer --break-system-packages
slither --version
```

---

## Setup — Contratos

```bash
cd contracts

# Instalar dependencias
forge install OpenZeppelin/openzeppelin-contracts
forge install foundry-rs/forge-std

# Compilar
forge build

# Tests
forge test -vv
```

---

## Tests y coverage

```bash
# Todos los tests
forge test -vv

# Test específico
forge test --match-contract LinkenTokenTest -vv

# Solo fuzz
forge test --match-test testFuzz -vv

# Coverage
forge coverage \
  --no-match-path "script/**" \
  --no-match-path "test/legacy/**" \
  --report summary

# Coverage con reporte HTML
forge coverage --report lcov
genhtml lcov.info --output-dir coverage-report
```

### Tests incluidos

| Suite | Tipos |
|---|---|
| `LinkenToken.t.sol` | Unit + Fuzz + Invariant |
| `ProjectRegistry.t.sol` | Unit + Fuzz |
| `OfferingContract.t.sol` | Unit + Fuzz |
| `DividendDistributor.t.sol` | Unit + Fuzz |
| `Integration.t.sol` | Integration + Fuzz |

---

## Análisis estático con Slither

```bash
cd contracts

slither src/ --config-file slither.config.json
```

`slither.config.json` recomendado:

```json
{
  "filter_paths": "lib/,src/legacy/",
  "solc_remaps": [
    "@openzeppelin/contracts/=lib/openzeppelin-contracts/contracts/"
  ]
}
```

---

## Setup — Frontend

Ver [FRONTEND.md](./frontend/FRONTEND.md) para instrucciones detalladas.

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
# http://localhost:3000
```

### Desarrollo local con Anvil

```bash
# Terminal 1
anvil

# Terminal 2
cd contracts
forge script script/DeployAll.s.sol:DeployAll \
  --rpc-url http://127.0.0.1:8545 \
  --private-key 0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80 \
  --broadcast

# Terminal 3 — copiar addresses del output al .env.local
cd frontend && npm run dev
```

---

## Variables de entorno

### contracts/.env

```bash
SEPOLIA_RPC_URL=https://ethereum-sepolia-rpc.publicnode.com
ETHERSCAN_API_KEY=
DEPLOYER_ADDRESS=

# Después del deploy:
# LINKEN_ADDRESS=
# REGISTRY_ADDRESS=
# OFFERING_ADDRESS=
# DISTRIBUTOR_ADDRESS=
```

### frontend/.env.local

```bash
NEXT_PUBLIC_USE_ANVIL=true
NEXT_PUBLIC_WALLETCONNECT_PROJECT_ID=
NEXT_PUBLIC_FACTORY_ADDRESS=
NEXT_PUBLIC_USDC_ADDRESS=
```

> Los archivos `.env` y `.env.local` están en `.gitignore`.
> Verificar con `git status` antes de cada push.

---

## Deploy

> ⚠️ En blockchain no hay rollbacks. El contrato queda en la red para siempre.
> Completar el checklist antes del deploy oficial.

### Checklist pre-deploy

- [ ] `forge test -vv` — todos los tests en verde
- [ ] `forge coverage` — coverage ≥ 95%
- [ ] Slither corrido y hallazgos revisados
- [ ] `.env` completo
- [ ] Wallet con SepoliaETH para gas
- [ ] Revisión en grupo del código final

### Deploy en Sepolia

```bash
cd contracts
source .env

forge script script/DeployAll.s.sol:DeployAll \
  --rpc-url $SEPOLIA_RPC_URL \
  --account dev \
  --broadcast

forge verify-contract $LINKEN_ADDRESS src/LinkenToken.sol:LinkenToken \
  --rpc-url $SEPOLIA_RPC_URL \
  --etherscan-api-key $ETHERSCAN_API_KEY \
  --chain sepolia
```

---

## Seguridad

| Item | Implementación |
|---|---|
| Reentrancy | `ReentrancyGuard` en todas las funciones de escritura |
| Patrón CEI | Checks → Effects → Interactions en todos los contratos |
| Overflow | Solidity 0.8.24 — revert automático, sin `unchecked` injustificado |
| Access control | `AccessControl` con roles explícitos |
| Sin loops | No hay iteración sobre arrays de holders |
| Sin ETH | Los contratos solo manejan USDC y LKN |
| Supply fijo | No hay `mint()` post-TGE |
| Soft cap | Inversores recuperan USDC si la ronda falla |
| `.env` protegido | Gitignore + secret detection en CI |
| Tests | Unit + Fuzz + Invariant + Integration |

---

## Decisiones de arquitectura (ADRs)

Las decisiones de diseño están documentadas en [`docs/`](./docs/).

| ADR | Título | Estado |
|---|---|---|
| [0001](./docs/0001-monorepo.md) | Monorepo | Vigente |
| [0002](./docs/0002-openzeppelin-v5.md) | OpenZeppelin v5 | Vigente |
| [0003](./docs/0003-solidity-0.8.24.md) | Solidity 0.8.24 | Vigente |
| [0004](./docs/0004-stack-frontend.md) | Stack frontend | Vigente |
| [0005](./docs/0005-access-control-roles.md) | AccessControl en lugar de Ownable | Vigente |
| [0006](./docs/0006-creator-role-factory.md) | CREATOR_ROLE para desarrolladores | Vigente |
| [0007](./docs/0007-factory-pattern-project-tokens.md) | Factory pattern para ProjectTokens | [Deprecado — ver ADR-0011](./docs/0011-simplificacion-token-global-lkn.md) |
| [0008](./docs/0008-pull-payment-dividends.md) | Pull payment para dividendos | Vigente |
| [0009](./docs/0009-linken-deprecado.md) | Linken.sol (v1) deprecado | Vigente |
| [0010](./docs/0010-refactor-ProjectFactory-createProject.md) | Simplificación token global | [Deprecado — ver ADR-0011](./docs/0011-simplificacion-token-global-lkn.md) |
| [0011](./docs/0011-simplificacion-token-global-lkn.md) | Token global LKN con TGE fijo | Vigente |
| [0012](./docs/0012-offering-contract-tge-flow.md) | OfferingContract: flujo TGE | Vigente |
| [0013](./docs/0013-lknsale-deprecado.md) | LKNSale deprecado | Vigente |
| [0014](./docs/0014-offering-registry-integration.md) | Integración OfferingContract ↔ ProjectRegistry | Vigente |
| [0015](./docs/0015-sin-pausable.md) | Eliminación de Pausable en todos los contratos | Vigente |

---

## Roadmap

- Límite máximo de compra por wallet en `OfferingContract` (anti-monopolio)
- Diagrama de interacción Web2 ↔ Web3
- Integración frontend completa con los nuevos contratos
- Oráculo de producción para kWh → dividendos automáticos
- Mercado secundario P2P de tokens
- Soporte multi-parque con múltiples instancias de `OfferingContract`
- Auditoría externa
- Deploy productivo en mainnet

---

## Changelog

| Versión | Fecha | Cambio |
|---|---|---|
| 0.3.1 | 2025-05 | Pausable quitado de los contratos |
| 0.3.0 | 2025-05 | OfferingContract + integración con ProjectRegistry |
| 0.2.0 | 2025-05 | Token global LKN con TGE fijo, sin mint |
| 0.1.0 | 2025-05 | Setup inicial: Linken ERC-20 + tests + frontend |

---

## Licencia

MIT

# Grafico Web2 + Web3

```bash
=== COMPONENTES ===

[UI: Frontend (Next.js)]
[EXT: MetaMask]
[BC: LinkenToken]
[BC: ProjectRegistry]
[BC: OfferingContract]
[BC: DividendDistributor]
[BC: USDC (Circle)]

=== FLUJO 1: Registro de proyecto (Admin) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: ProjectRegistry]
[BC: ProjectRegistry] ----(3)----> [UI: Frontend]

1) [off-chain] Admin completa formulario: nombre, descripción, earlyBirdPrice, standardPrice, owner
2) [on-chain]  call: registerProject(name, description, owner, earlyBirdPrice, standardPrice)
3) [event]     emit ProjectRegistered(projectId, owner, name, earlyBirdPrice, standardPrice)

=== FLUJO 2: Setup de ronda (Emisor) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: OfferingContract]
[UI: Frontend] ----(3)----> [EXT: MetaMask]
[EXT: MetaMask] ----(4)----> [BC: LinkenToken]
[UI: Frontend] ----(5)----> [EXT: MetaMask]
[EXT: MetaMask] ----(6)----> [BC: OfferingContract]
[UI: Frontend] ----(7)----> [EXT: MetaMask]
[EXT: MetaMask] ----(8)----> [BC: ProjectRegistry]
[UI: Frontend] ----(9)----> [EXT: MetaMask]
[EXT: MetaMask] ----(10)----> [BC: OfferingContract]

1)  [off-chain] Admin deploya OfferingContract con: lkn, usdc, treasury, tokenPrice, softCap, hardCap, deadline, registry, projectId
2)  [on-chain]  constructor(...)
3)  [off-chain] Admin aprueba LKN al OfferingContract
4)  [on-chain]  call: approve(offeringContract, amount)
5)  [off-chain] Emisor deposita LKN en escrow
6)  [on-chain]  call: deposit(lknAmount)
7)  [off-chain] Admin otorga OFFERING_ROLE al OfferingContract en el Registry
8)  [on-chain]  call: grantRole(OFFERING_ROLE, offeringContract)
9)  [off-chain] Emisor abre la ronda
10) [on-chain]  call: openRound()

=== FLUJO 3: Compra de LKN (Inversor — etapa FUNDING) ===

[UI: Frontend] ----(1)----> [BC: ProjectRegistry]
<----(2)---- [BC: ProjectRegistry]
[UI: Frontend] ----(3)----> [EXT: MetaMask]
[EXT: MetaMask] ----(4)----> [BC: USDC]
[UI: Frontend] ----(5)----> [EXT: MetaMask]
[EXT: MetaMask] ----(6)----> [BC: OfferingContract]
[BC: OfferingContract] ----(7)----> [BC: USDC]
[BC: OfferingContract] ----(8)----> [BC: LinkenToken]
[BC: OfferingContract] ----(9)----> [UI: Frontend]

1)  [off-chain] Frontend consulta precio del proyecto
2)  [on-chain]  view: currentPrice(projectId) → earlyBirdPrice
3)  [off-chain] Inversor aprueba USDC al OfferingContract
4)  [on-chain]  call: approve(offeringContract, usdcAmount)
5)  [off-chain] Inversor compra LKN
6)  [on-chain]  call: buy(usdcAmount)
7)  [on-chain]  call: safeTransferFrom(investor, treasury, usdcAmount)
8)  [on-chain]  call: safeTransfer(investor, lknAmount)  [lknAmount = usdcAmount * 1e18 / tokenPrice]
9)  [event]     emit TokensPurchased(buyer, usdcAmount, lknAmount)

=== FLUJO 4: Cierre exitoso por hard cap (automático) ===

[BC: OfferingContract] ----(1)----> [BC: ProjectRegistry]
[BC: OfferingContract] ----(2)----> [UI: Frontend]
[BC: ProjectRegistry]  ----(3)----> [UI: Frontend]

1)  [on-chain]  call: activateProject(projectId)  [dentro de buy() al alcanzar hardCap]
2)  [event]     emit RoundFinalized(totalRaised, lknSold)
3)  [event]     emit StageChanged(projectId, ACTIVE)

=== FLUJO 5: Cierre exitoso por finalize (emisor supera softCap) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: OfferingContract]
[BC: OfferingContract] ----(3)----> [BC: LinkenToken]
[BC: OfferingContract] ----(4)----> [BC: ProjectRegistry]
[BC: OfferingContract] ----(5)----> [UI: Frontend]
[BC: ProjectRegistry]  ----(5)----> [UI: Frontend]

1)  [off-chain] Emisor llama finalize() — puede hacerlo cuando totalRaised >= softCap
2)  [on-chain]  call: finalize()
3)  [on-chain]  call: safeTransfer(emisor, unsoldLKN)
4)  [on-chain]  call: activateProject(projectId) → stage = ACTIVE  [automático dentro de finalize()]
5)  [event]     emit RoundFinalized(totalRaised, lknSold)
                emit StageChanged(projectId, ACTIVE)
                emit UnsoldLKNReturned(emisor, amount)  [si hay LKN no vendidos]

=== FLUJO 6: Consulta post-apertura (Inversor dormido) ===

[UI: Frontend] ----(1)----> [BC: ProjectRegistry]
<----(2)---- [BC: ProjectRegistry]

1)  [off-chain] Inversor vuelve meses después y consulta el proyecto
2)  [on-chain]  view: currentPrice(projectId) → standardPrice  [stage = ACTIVE]

=== FLUJO 7: Ronda fallida — refund (Inversor) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: OfferingContract]
[BC: OfferingContract] ----(3)----> [BC: USDC]
[BC: OfferingContract] ----(4)----> [UI: Frontend]

1)  [off-chain] Deadline pasó sin alcanzar softCap — inversor llama refund
2)  [on-chain]  call: refund()
3)  [on-chain]  call: safeTransferFrom(treasury, investor, usdcAmount)  [treasury devuelve USDC]
4)  [event]     emit Refunded(investor, usdcAmount)

=== FLUJO 8: Distribución de dividendos (Plataforma) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: USDC]
[UI: Frontend] ----(3)----> [EXT: MetaMask]
[EXT: MetaMask] ----(4)----> [BC: DividendDistributor]
[BC: DividendDistributor] ----(5)----> [BC: USDC]
[BC: DividendDistributor] ----(6)----> [UI: Frontend]

1)  [off-chain] Plataforma aprueba USDC al DividendDistributor
2)  [on-chain]  call: approve(distributorAddress, amount)
3)  [off-chain] Plataforma deposita dividendos del período
4)  [on-chain]  call: depositDividends(usdcAmount)
5)  [on-chain]  call: safeTransferFrom(platform, distributor, usdcAmount)
6)  [event]     emit DividendsDeposited(depositor, amount)
                [magnifiedDividendPerShare += amount * 2^128 / totalSupply]

=== FLUJO 9: Retiro de dividendos (Inversor) ===

[UI: Frontend] ----(1)----> [BC: DividendDistributor]
<----(2)---- [BC: DividendDistributor]
[UI: Frontend] ----(3)----> [EXT: MetaMask]
[EXT: MetaMask] ----(4)----> [BC: DividendDistributor]
[BC: DividendDistributor] ----(5)----> [BC: USDC]
[BC: DividendDistributor] ----(6)----> [UI: Frontend]

1)  [off-chain] Frontend consulta dividendos pendientes del inversor
2)  [on-chain]  view: pendingDividends(holderAddress) → pendingUSDC
3)  [off-chain] Inversor decide retirar
4)  [on-chain]  call: claimDividends()
5)  [on-chain]  call: safeTransfer(investor, pendingUSDC)
6)  [event]     emit DividendsWithdrawn(holder, amount)

=== FLUJO 10: Transferencia de LKN entre inversores (hook de dividendos) ===

[UI: Frontend] ----(1)----> [EXT: MetaMask]
[EXT: MetaMask] ----(2)----> [BC: LinkenToken]
[BC: LinkenToken] ----(3)----> [BC: DividendDistributor]
[BC: LinkenToken] ----(4)----> [UI: Frontend]

1)  [off-chain] Inversor A transfiere LKN a Inversor B
2)  [on-chain]  call: transfer(investorB, amount)
3)  [on-chain]  call: onTokenTransfer(from, to, amount)
                [magnifiedDividendCorrections[from] += delta]
                [magnifiedDividendCorrections[to]   -= delta]
                [preserva derechos adquiridos antes de la transferencia]
4)  [event]     emit Transfer(from, to, amount)  [estándar ERC-20]
```

# Estrategias para prevenir monopolio

## KYC + límites por identidad (Web2)

Estándar en plataformas reguladas (Securitize, Tokeny, Republic). Un backend verifica identidad (DNI/pasaporte) y asigna un cupo máximo de inversión por persona real, no por wallet. Una persona puede tener mil wallets pero una sola identidad verificada.

## Whitelist con cupos (Web2 + Web3 híbrido)

El admin aprueba wallets y opcionalmente les asigna un límite individual. Esto se implementa en el contrato como un mapping(address => uint256) public maxAllocation que el admin configura off-chain después del KYC. Es el modelo de Reg D / Reg S en securities tokenizadas de EE.UU.

## Rondas con tiempo mínimo entre compras (Web3)

Cada wallet puede comprar máximo X USDC cada Y horas. Dificulta la acumulación rápida sin eliminar la posibilidad de invertir mucho a lo largo del tiempo.

## Precio dinámico por volumen (Web3 — bonding curve)

Cuanto más compra un inversor en una sola ronda, más caro le sale cada token. Desincentiva la acumulación masiva naturalmente. Uniswap y Balancer usan variantes de esto.

## Oversubscription + prorrateo (Web2)

Si la demanda supera el hard cap, se acepta todo y al cierre se prorratean los tokens proporcionalmente. Nadie puede "acaparar" porque todos reciben menos si hay mucha demanda. Es el modelo de las IPOs tradicionales y de plataformas como CoinList.


# La recomendacion: híbrido KYC + whitelist:

1. **Off-chain**: la plataforma verifica identidad y aprueba la wallet
2. **On-chain**: el `OfferingContract` tiene un `mapping(address => bool) public whitelisted` y solo wallets aprobadas pueden llamar `buy()`.

**Opcional**: `mapping(address => uint256) public maxAllocation` para límites individualizados