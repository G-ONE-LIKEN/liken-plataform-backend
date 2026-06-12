# 0003 - Solidity 0.8.24

## Contexto
La version del compilador afecta las garantias de seguridad disponibles por defecto
y la compatibilidad con las dependencias.

## Decision
Usar Solidity 0.8.24, ultima version estable al momento del desarrollo.

## Consecuencias
- Overflow y underflow revierten por defecto, sin necesidad de SafeMath.
- El uso de `unchecked` queda reservado para casos explicitamente justificados.
- Compatible con OpenZeppelin v5 y con el compilador configurado en `foundry.toml`.
- Las versiones futuras del compilador podrian introducir cambios de comportamiento que requieran revision.