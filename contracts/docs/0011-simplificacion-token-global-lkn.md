# 0010 - Simplificación: token global LKN en lugar de subtokens por proyecto

## Contexto
El diseño original emitía un ERC-20 distinto por cada proyecto energético
(via ProjectFactory + ProjectToken). Esto generaba complejidad operativa:
múltiples contratos a deployar y verificar, ABIs distintos por proyecto,
y una economía de tokens difícil de explicar y auditar.

En consulta con los profesores, se acordó simplificar el modelo para
hacerlo más didáctico y enfocado en los conceptos core de blockchain.

## Decisión
Reemplazar el sistema de subtokens por un **token global único: Linken (LKN)**.

### Cambios respecto al diseño anterior

| Aspecto | Antes | Ahora |
|---|---|---|
| Token por proyecto | Sí (ProjectToken ERC-20) | No |
| Token global | No | Sí (LKN) |
| Supply | Cap por proyecto | Infinito — owner mintea según demanda |
| Variaciones de precio | No definidas | No hay — precio fijo LKN/USDC |
| Factory | ProjectFactory deployaba tokens | ProjectRegistry solo registra proyectos |
| Early bird | No existía | Bonificación en etapa FUNDING del proyecto |

### Modelo resultante

Un inversor compra LKN pagando USDC a través de `LKNSale`. El contrato
aplica la tasa de conversión correspondiente según la etapa del proyecto:
- **FUNDING**: precio reducido (early bird), más LKN por el mismo USDC
- **ACTIVE**: precio estándar de la tabla de conversión

Los LKN son fungibles globalmente — no quedan bloqueados en un proyecto
específico. La asociación inversor↔proyecto queda registrada como evento
on-chain (`TokensPurchased`) y puede ser indexada off-chain.

El `DividendDistributor` se mantiene sin cambios: reparte USDC
proporcionalmente entre todos los holders de LKN, independientemente
de en qué proyecto invirtieron.

### Contratos resultantes
* **`LinkenToken.sol`**: ERC-20 global, supply infinito, Pausable, AccessControl.
* **`ProjectRegistry.sol`**: registra proyectos con etapa FUNDING/ACTIVE y precios.
* **`LKNSale.sol`**: tabla de conversión LKN/USDC + early bird + compra.
* **`DividendDistributor.sol`**: sin cambios respecto al diseño anterior.

### Contratos deprecados (movidos a legacy/)
* **`ProjectToken.sol`**: reemplazado por `LinkenToken.sol`.
* **`ProjectFactory.sol`**: reemplazado por `ProjectRegistry.sol`.

## Consecuencias
- El sistema es más simple de explicar: "comprás LKN para participar en proyectos".
- Un solo contrato ERC-20 a deployar y verificar en lugar de uno por proyecto.
- Los dividendos se reparten entre todos los holders de LKN — no hay distinción por proyecto a nivel on-chain. De necesitarse a futuro granularidad por proyecto, se puede agregar un segundo distributor.
- El precio fijo elimina la volatilidad del token, lo que simplifica la contabilidad pero también limita el modelo económico real.
- El early bird es una decisión de negocio del admin del proyecto el contrato lo implementa como un multiplicador de tokens en etapa **FUNDING**.
- Supply infinito requiere disciplina en el uso de `mint` — el owner puede emitir libremente, lo que en producción real requiere governance.