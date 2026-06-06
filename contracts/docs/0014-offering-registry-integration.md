# 0014 - Integración automática entre OfferingContract y ProjectRegistry

## Contexto
El ProjectRegistry mantiene el ciclo de vida de cada proyecto energético (`FUNDING → ACTIVE → PAUSED`) y los precios asociados a cada etapa.

El OfferingContract gestiona la ronda de venta primaria de tokens.

Sin integración entre ambos, el cambio de etapa `FUNDING → ACTIVE` requería una transacción manual del admin después de cada ronda exitosa. Esto genera dos problemas:

- Riesgo operativo: el admin podría olvidarse o demorar el cambio, mostrando el precio incorrecto a los inversores en el frontend.
- Inconsistencia de estado: la ronda podría estar finalizada on-chain pero el Registry seguir mostrando stage = FUNDING con el precio bajo, permitiendo que nuevos inversores vean un precio desactualizado.

## Decisión
El `OfferingContract` llama automáticamente a `ProjectRegistry.activateProject()`
cuando una ronda se finaliza exitosamente, ya sea por:
- El emisor llamando `finalize()` después de superar el soft cap.
- El hard cap siendo alcanzado automáticamente durante `buy()`.

Para que esto sea seguro, el `ProjectRegistry` expone `activateProject()` protegido por `OFFERING_ROLE` — solo contratos autorizados explícitamente por el admin pueden cambiar la etapa de FUNDING a ACTIVE.

## Flujo completo

### SETUP

1. Admin registra proyecto en ProjectRegistry → `stage = FUNDING`, `earlyBirdPrice = $2/LKN`, `standardPrice = $10/LKN`.
2. Admin despliega OfferingContract con `tokenPrice = $2`, `projectId = N`.
3. Admin otorga `OFFERING_ROLE` al OfferingContract en el Registry.

### PRE-APERTURA (stage = FUNDING)

1. Frontend muestra `precio = earlyBirdPrice` ($2/LKN)
2. Inversores compran `LKN` a precio reducido.

#### CIERRE POR HARD CAP (automático en buy())

1. `totalRaised` alcanza `hardCap`.
2. OfferingContract marca `state = FINALIZED`.
3. OfferingContract llama `registry.activateProject(projectId) → Registry`  cambia `stage = ACTIVE` automáticamente

#### CIERRE POR FINALIZE (manual del emisor)

1. Emisor verifica `totalRaised >= softCap`.
2. Emisor llama `finalize()`.
3. OfferingContract devuelve `LKN` no vendidos al emisor.
4. OfferingContract llama `registry.activateProject(projectId)` → Registry cambia `stage = ACTIVE` automáticamente

### POST-APERTURA (stage = ACTIVE)

1. Frontend muestra `precio = standardPrice` ($10/LKN)
2. El inversor que no compró en la ronda **early bird** ve el precio actualizado correctamente.
3. Si hay nueva ronda, el OfferingContract nuevo usa `tokenPrice = $10`.

## Rol OFFERING_ROLE

`OFFERING_ROLE` en el ProjectRegistry es el mecanismo de seguridad que impide que cualquier contrato externo pueda activar proyectos arbitrariamente.

El admin otorga este rol explícitamente a cada OfferingContract después de deployarlo:

```solidity
registry.grantRole(registry.OFFERING_ROLE(), address(offeringContract));
```

Si el admin revoca el rol, el OfferingContract no puede activar el proyecto aunque la ronda sea exitosa — el admin recupera control manual.

## Consecuencias

- El cambio de etapa es atómico con la finalización de la ronda — no hay ventana de inconsistencia entre ambos contratos.
- El frontend siempre muestra el precio correcto sin intervención manual.
- Un proyecto puede tener múltiples rondas sucesivas — cada una con su
  propio OfferingContract y su propio OFFERING_ROLE otorgado.
- Si la ronda falla (soft cap no alcanzado), el proyecto permanece en `FUNDING` — puede lanzar una nueva ronda más adelante.
- `activateProject()` solo permite la transición `FUNDING → ACTIVE`, no puede revertir un proyecto ya activo ni pausarlo.