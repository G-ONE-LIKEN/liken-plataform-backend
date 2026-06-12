# 0010 - Uso de CREATE2 en ProjectFactory para cumplir con Checks-Effects-Interactions

## Contexto

El reporte de analisis estatico de Slither identifico un hallazgo de Reentrancy Benign (reentrancia benigna) en la funcion `createProject` de `ProjectFactory.sol`. El flujo original realizaba primero el despliegue del contrato hijo (`new ProjectToken(...)`) y posteriormente modificaba el estado de la factory guardando la informacion en los mappings projects y tokenToProject.

Aunque la funcion ya contaba con el modificador nonReentrant de OpenZeppelin para evitar exploits maliciosos, la arquitectura violaba el principio fundamental de Checks-Effects-Interactions (CEI). Dado que la direccion del token es requerida para persistir los datos en el estado de la factory, no era posible mover un despliegue estandar basado en CREATE al final de la funcion sin romper la logica del negocio.

## Decision

Se decide refactorizar la funcion createProject implementando el codigo de creacion `CREATE2 (new ProjectToken{salt: salt}(...))` de Solidity.

Esta modificacion introduce las siguientes mejoras:

- Prediccion de direcciones determinista: Se calcula la direccion que adoptara el nuevo token de forma matematica utilizando el projectId como salt y el creationCode del contrato, antes de efectuar el deploy real.

- Alineacion estricta con CEI: Al conocer la direccion de antemano, se procesan primero todas las validaciones (Checks), luego se registran los mappings del estado interno y se emite el evento (Effects), y finalmente se ejecuta el despliegue del token como llamada externa (Interactions).

## Consecuencias

- Se elimina por completo la alerta de reentrancia en las auditorias automaticas de Slither, mejorando el puntaje de seguridad del repositorio.

- El flujo del contrato se vuelve mas robusto y predecible frente a futuras integraciones de terceros.

- Se introduce una restriccion implicita: no se pueden desplegar dos tokens con los mismos parametros exactos bajo el mismo projectId, lo cual actua como una salvaguarda de unicidad nativa en la EVM.

- Las pruebas en Foundry (forge test) que involucren el deploy de proyectos deben contemplar este comportamiento determinista si se modifican los argumentos del constructor del token.