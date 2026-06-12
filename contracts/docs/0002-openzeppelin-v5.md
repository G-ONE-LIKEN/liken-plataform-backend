# 0002 - OpenZeppelin v5

## Contexto
El contrato necesita implementaciones auditadas de ERC-20, Ownable, Pausable y ReentrancyGuard.
Existen multiples versiones de OpenZeppelin con APIs distintas.

## Decision
Usar OpenZeppelin Contracts v5 (ultima version estable con soporte activo).

## Consecuencias
- `ERC20Pausable` usa `_update` en lugar del deprecado `_beforeTokenTransfer` de v4.
- `Ownable` requiere pasar `initialOwner` explicitamente en el constructor, eliminando el patron implicito `msg.sender` que fue fuente de bugs historicos.
- `ReentrancyGuard` tiene menor overhead de gas que en v4.
- Incompatible con codigo escrito para OZ v4 sin migracion.