# 0010 - Uso de CREATE2 en ProjectFactory para cumplir con Checks-Effects-Interactions

## Contexto

El reporte de análisis estático de Slither identificó un hallazgo de Reentrancy Benign (reentrancia benigna) en la función `createProject` de `ProjectFactory.sol`. El flujo original realizaba primero el despliegue del contrato hijo (`new ProjectToken(...)`) y posteriormente modificaba el estado de la factory guardando la información en los mappings projects y tokenToProject.

Aunque la función ya contaba con el modificador nonReentrant de OpenZeppelin para evitar exploits maliciosos, la arquitectura violaba el principio fundamental de Checks-Effects-Interactions (CEI). Dado que la dirección del token es requerida para persistir los datos en el estado de la factory, no era posible mover un despliegue estándar basado en CREATE al final de la función sin romper la lógica del negocio.

## Decisión

Se decide refactorizar la función createProject implementando el código de creación `CREATE2 (new ProjectToken{salt: salt}(...))` de Solidity.

Esta modificación introduce las siguientes mejoras:

- Predicción de direcciones determinista: Se calcula la dirección que adoptará el nuevo token de forma matemática utilizando el projectId como salt y el creationCode del contrato, antes de efectuar el deploy real.

- Alineación estricta con CEI: Al conocer la dirección de antemano, se procesan primero todas las validaciones (Checks), luego se registran los mappings del estado interno y se emite el evento (Effects), y finalmente se ejecuta el despliegue del token como llamada externa (Interactions).

## Consecuencias

- Se elimina por completo la alerta de reentrancia en las auditorías automáticas de Slither, mejorando el puntaje de seguridad del repositorio.

- El flujo del contrato se vuelve más robusto y predecible frente a futuras integraciones de terceros.

- Se introduce una restricción implícita: no se pueden desplegar dos tokens con los mismos parámetros exactos bajo el mismo projectId, lo cual actúa como una salvaguarda de unicidad nativa en la EVM.

- Las pruebas en Foundry (forge test) que involucren el deploy de proyectos deben contemplar este comportamiento determinista si se modifican los argumentos del constructor del token.