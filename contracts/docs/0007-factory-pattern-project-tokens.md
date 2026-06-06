# 0007 - Factory pattern para despliegue de ProjectTokens

## Contexto
La plataforma soporta múltiples proyectos energéticos simultáneos. Cada proyecto
necesita su propio token ERC-20 con supply, nombre y símbolo independientes.
Existen dos enfoques principales para manejar esto on-chain.

## Decisión
Usar el patrón Factory: un contrato `ProjectFactory` central despliega una nueva
instancia de `ProjectToken` por cada proyecto registrado.

Alternativa descartada — contrato único con IDs internos:
Un solo contrato maneja todos los proyectos como balances internos (similar a ERC-1155).
Se descartó porque los tokens no serían ERC-20 estándar, lo que impide que MetaMask
los muestre nativamente, que el marketplace opere con `approve/transferFrom` estándar,
y que el DividendDistributor calcule participaciones con `balanceOf`.

## Consecuencias
- Cada proyecto tiene su propia address de contrato, visible en Etherscan de forma independiente.
- Los tokens son ERC-20 estándar: compatibles con MetaMask, wallets externas y el marketplace.
- El aislamiento de contratos limita el blast radius de un bug: un ProjectToken comprometido
  no afecta a los demás ni a la Factory.
- Cada deploy de ProjectToken tiene un costo en gas. Para la escala actual (testnet Sepolia)
  esto no es un problema; en mainnet convendría medir y optimizar.
- La Factory mantiene un registro `projectId => ProjectInfo` que el frontend usa para
  listar y filtrar proyectos sin depender de una base de datos centralizada.