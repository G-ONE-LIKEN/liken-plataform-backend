# 0015 - Eliminación de Pausable en todos los contratos

## Contexto
Los contratos originalmente implementaban Pausable de OpenZeppelin,
permitiendo al admin detener transferencias, mints, burns y depósitos
en caso de emergencia.

En revisión con los profesores se identificó que esta capacidad genera
un problema de confianza crítico para una plataforma de inversión:
si el fideicomiso puede pausar transferencias en cualquier momento,
los inversores no tienen garantía de poder mover sus tokens.
Esto se conoce en DeFi como "admin risk" y es un red flag en cualquier
auditoría seria de contratos.

## Decisión
Eliminar Pausable y todas sus funciones (pause, unpause, whenNotPaused)
de los cuatro contratos productivos:
- LinkenToken.sol
- ProjectRegistry.sol
- OfferingContract.sol
- DividendDistributor.sol

## Consecuencias
- Los inversores tienen garantía de que sus tokens siempre son transferibles.
- El admin no puede bloquear operaciones unilateralmente.
- Se reduce la superficie de ataque — menos funciones privilegiadas.
- En caso de bug crítico, el mecanismo de defensa es el upgrade pattern
  o el deploy de un contrato nuevo, no la pausa. Para esta versión
  académica se acepta ese trade-off.
- Los tests de pausable fueron eliminados de todas las suites.