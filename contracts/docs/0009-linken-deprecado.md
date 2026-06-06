# 0009 - Linken.sol deprecado en favor de ProjectToken.sol

## Contexto
Linken.sol fue el primer contrato ERC-20 del proyecto, desarrollado como
prototipo para validar el patrón de seguridad: ReentrancyGuard, Pausable,
Ownable, cap de supply y tests completos con Foundry.

## Decisión
Linken.sol queda deprecado. Su sucesor es ProjectToken.sol, que implementa
el mismo patrón de seguridad con dos mejoras:

- AccessControl en lugar de Ownable, permitiendo roles separados para
  mint, pause y administración.
- Constructor parametrizado: nombre, símbolo, supply y owner se definen
  al momento del deploy, permitiendo múltiples instancias via ProjectFactory.

Los archivos se mueven a src/legacy/, test/legacy/ y script/legacy/
para preservar el historial de decisiones sin que interfieran con el
build y los tests del sistema productivo.

## Consecuencias
- forge test no ejecuta los tests de Linken salvo que se apunte
  explícitamente a legacy/.
- El historial de git preserva la evolución del diseño.
- Los compañeros que revisen el repo pueden ver el prototipo original
  como referencia del proceso de desarrollo.