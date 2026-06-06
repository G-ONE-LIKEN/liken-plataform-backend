# 0003 - Solidity 0.8.24

## Contexto
La versión del compilador afecta las garantías de seguridad disponibles por defecto
y la compatibilidad con las dependencias.

## Decisión
Usar Solidity 0.8.24, última versión estable al momento del desarrollo.

## Consecuencias
- Overflow y underflow revierten por defecto, sin necesidad de SafeMath.
- El uso de `unchecked` queda reservado para casos explícitamente justificados.
- Compatible con OpenZeppelin v5 y con el compilador configurado en `foundry.toml`.
- Las versiones futuras del compilador podrían introducir cambios de comportamiento que requieran revisión.