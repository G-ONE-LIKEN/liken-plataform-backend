# 0009 - Linken.sol deprecado en favor de ProjectToken.sol

## Contexto
Linken.sol fue el primer contrato ERC-20 del proyecto, desarrollado como
prototipo para validar el patron de seguridad: ReentrancyGuard, Pausable,
Ownable, cap de supply y tests completos con Foundry.

## Decision
Linken.sol queda deprecado. Su sucesor es ProjectToken.sol, que implementa
el mismo patron de seguridad con dos mejoras:

- AccessControl en lugar de Ownable, permitiendo roles separados para
  mint, pause y administracion.
- Constructor parametrizado: nombre, simbolo, supply y owner se definen
  al momento del deploy, permitiendo multiples instancias via ProjectFactory.

Los archivos se mueven a src/legacy/, test/legacy/ y script/legacy/
para preservar el historial de decisiones sin que interfieran con el
build y los tests del sistema productivo.

## Consecuencias
- forge test no ejecuta los tests de Linken salvo que se apunte
  explicitamente a legacy/.
- El historial de git preserva la evolucion del diseño.
- Los compañeros que revisen el repo pueden ver el prototipo original
  como referencia del proceso de desarrollo.