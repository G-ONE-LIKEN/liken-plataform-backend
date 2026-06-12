# 0010 - Simplificacion: token global LKN en lugar de subtokens por proyecto

## Contexto
El diseño original emitia un ERC-20 distinto por cada proyecto energético
(via ProjectFactory + ProjectToken). Esto generaba complejidad operativa:
multiples contratos a deployar y verificar, ABIs distintos por proyecto,
y una economia de tokens dificil de explicar y auditar.

En consulta con los profesores, se acordo simplificar el modelo para
hacerlo mas didactico y enfocado en los conceptos core de blockchain.

## Decision
Reemplazar el sistema de subtokens por un **token global unico: Linken (LKN)**.

### Cambios respecto al diseño anterior

| Aspecto | Antes | Ahora |
|---|---|---|
| Token por proyecto | Si (ProjectToken ERC-20) | No |
| Token global | No | Si (LKN) |
| Supply | Cap por proyecto | Infinito — owner mintea segun demanda |
| Variaciones de precio | No definidas | No hay — precio fijo LKN/USDC |
| Factory | ProjectFactory deployaba tokens | ProjectRegistry solo registra proyectos |
| Early bird | No existia | Bonificacion en etapa FUNDING del proyecto |

### Modelo resultante

Un inversor compra LKN pagando USDC a través de `LKNSale`. El contrato
aplica la tasa de conversion correspondiente segun la etapa del proyecto:
- **FUNDING**: precio reducido (early bird), mas LKN por el mismo USDC
- **ACTIVE**: precio estandar de la tabla de conversion

Los LKN son fungibles globalmente — no quedan bloqueados en un proyecto
especifico. La asociacion inversor↔proyecto queda registrada como evento
on-chain (`TokensPurchased`) y puede ser indexada off-chain.

El `DividendDistributor` se mantiene sin cambios: reparte USDC
proporcionalmente entre todos los holders de LKN, independientemente
de en qué proyecto invirtieron.

### Contratos resultantes
* **`LinkenToken.sol`**: ERC-20 global, supply infinito, Pausable, AccessControl.
* **`ProjectRegistry.sol`**: registra proyectos con etapa FUNDING/ACTIVE y precios.
* **`LKNSale.sol`**: tabla de conversion LKN/USDC + early bird + compra.
* **`DividendDistributor.sol`**: sin cambios respecto al diseño anterior.

### Contratos deprecados (movidos a legacy/)
* **`ProjectToken.sol`**: reemplazado por `LinkenToken.sol`.
* **`ProjectFactory.sol`**: reemplazado por `ProjectRegistry.sol`.

## Consecuencias
- El sistema es mas simple de explicar: "compras LKN para participar en proyectos".
- Un solo contrato ERC-20 a deployar y verificar en lugar de uno por proyecto.
- Los dividendos se reparten entre todos los holders de LKN — no hay distincion por proyecto a nivel on-chain. De necesitarse a futuro granularidad por proyecto, se puede agregar un segundo distributor.
- El precio fijo elimina la volatilidad del token, lo que simplifica la contabilidad pero también limita el modelo economico real.
- El early bird es una decision de negocio del admin del proyecto el contrato lo implementa como un multiplicador de tokens en etapa **FUNDING**.
- Supply infinito requiere disciplina en el uso de `mint` — el owner puede emitir libremente, lo que en produccion real requiere governance.