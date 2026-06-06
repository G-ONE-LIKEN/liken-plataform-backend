# 0001 - Monorepo

## Contexto
El proyecto tiene dos componentes: contratos Solidity y un frontend web.
Ambos necesitan compartir el ABI del contrato y evolucionar juntos.
Los integrantes del grupo deben poder clonar un único repositorio y tener todo listo.

## Decisión
Usar un monorepo con dos carpetas raíz: `contracts/` y `frontend/`.

## Consecuencias
- Un solo `git clone` da acceso a todo el proyecto.
- El ABI y la address del contrato se referencian desde un único lugar (`frontend/src/lib/contract.ts`).
- Los PRs pueden tocar contrato y frontend en el mismo commit, facilitando la revisión.
- El CI necesita `working-directory` explícito por job para no confundir los toolchains.