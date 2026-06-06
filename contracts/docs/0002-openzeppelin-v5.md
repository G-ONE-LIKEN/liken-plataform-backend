# 0002 - OpenZeppelin v5

## Contexto
El contrato necesita implementaciones auditadas de ERC-20, Ownable, Pausable y ReentrancyGuard.
Existen múltiples versiones de OpenZeppelin con APIs distintas.

## Decisión
Usar OpenZeppelin Contracts v5 (última versión estable con soporte activo).

## Consecuencias
- `ERC20Pausable` usa `_update` en lugar del deprecado `_beforeTokenTransfer` de v4.
- `Ownable` requiere pasar `initialOwner` explícitamente en el constructor, eliminando el patrón implícito `msg.sender` que fue fuente de bugs históricos.
- `ReentrancyGuard` tiene menor overhead de gas que en v4.
- Incompatible con código escrito para OZ v4 sin migración.