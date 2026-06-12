# 0006 - Desarrolladores crean proyectos directamente con CREATOR_ROLE

## Contexto
Los desarrolladores registrados en la plataforma necesitan poder crear proyectos
energéticos sin requerir una transaccion manual de aprobacion del admin por cada
solicitud. El proceso de validacion del desarrollador ocurre off-chain (registro
en la plataforma), pero su habilitacion on-chain debe ser explicita y revocable.

Alternativas consideradas:
1. `onlyOwner` en `createProject`: el admin firma cada creacion. Seguro pero
   genera un cuello de botella operativo y mala UX.
2. Sin restricciones: cualquiera puede crear proyectos. Riesgo de spam y proyectos
   fraudulentos que dañen la reputacion de la plataforma.
3. `CREATOR_ROLE`: el admin asigna el rol una sola vez por desarrollador validado.
   El desarrollador opera de forma autonoma a partir de ahi.

## Decision
Usar `CREATOR_ROLE` en `ProjectFactory`. El flujo es:
1. Desarrollador se registra y es validado off-chain.
2. Admin ejecuta `grantRole(CREATOR_ROLE, developerAddress)` — una sola transaccion.
3. El desarrollador llama `createProject()` directamente desde su wallet.
4. Si el desarrollador es dado de baja, admin ejecuta `revokeRole(CREATOR_ROLE, developerAddress)`.

## Consecuencias
- El desarrollador tiene autonomia operativa una vez validado.
- El admin no es un cuello de botella para cada proyecto nuevo.
- La revocacion es inmediata y on-chain: un desarrollador dado de baja no puede
  crear nuevos proyectos desde el momento en que se le revoca el rol.
- Los proyectos ya creados por un desarrollador revocado no se ven afectados:
  sus ProjectTokens siguen existiendo con sus propios roles.
- Requiere que el proceso de validacion off-chain sea robusto, ya que la
  asignacion de CREATOR_ROLE es la unica barrera on-chain.