# 0001 - Monorepo

## Contexto
El proyecto tiene dos componentes: contratos Solidity y un frontend web.
Ambos necesitan compartir el ABI del contrato y evolucionar juntos.
Los integrantes del grupo deben poder clonar un unico repositorio y tener todo listo.

## Decision
Usar un monorepo con dos carpetas raiz: `contracts/` y `frontend/`.

## Consecuencias
- Un solo `git clone` da acceso a todo el proyecto.
- El ABI y la address del contrato se referencian desde un unico lugar (`frontend/src/lib/contract.ts`).
- Los PRs pueden tocar contrato y frontend en el mismo commit, facilitando la revision.
- El CI necesita `working-directory` explicito por job para no confundir los toolchains.