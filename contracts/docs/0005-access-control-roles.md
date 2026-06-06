# 0005 - AccessControl en lugar de Ownable para ProjectToken y ProjectFactory

## Contexto
El sistema tiene múltiples actores con permisos distintos sobre los mismos contratos:
- La plataforma necesita poder pausar cualquier proyecto en caso de emergencia.
- El creador del proyecto necesita poder mintear tokens de su propio proyecto.
- Los desarrolladores registrados necesitan poder crear proyectos sin requerir
  aprobación manual del admin por cada operación.

Ownable solo permite un único owner, lo que obliga a elegir entre darle control
total al creador o a la plataforma, sin poder tener ambos con permisos acotados.

## Decisión
Usar OpenZeppelin AccessControl con roles explícitos en lugar de Ownable:

**ProjectToken:**
- `MINTER_ROLE` — creador del proyecto: puede mintear tokens.
- `PAUSER_ROLE` — creador del proyecto y plataforma: pueden pausar/despausar.
- `DEFAULT_ADMIN_ROLE` — plataforma: puede otorgar y revocar cualquier rol.

**ProjectFactory:**
- `CREATOR_ROLE` — desarrolladores aprobados: pueden crear nuevos proyectos.
- `DEFAULT_ADMIN_ROLE` — plataforma: puede otorgar y revocar CREATOR_ROLE.

## Consecuencias
- La plataforma puede pausar cualquier ProjectToken como circuit-breaker de emergencia
  sin necesidad de ser owner del token.
- El creador puede mintear sin tener control administrativo total sobre el contrato.
- Los roles son revocables individualmente: se puede quitar MINTER_ROLE a un creador
  sin afectar PAUSER_ROLE ni los demás roles.
- Agrega algo de complejidad al deploy: la Factory debe asignar los roles correctos
  al momento de crear cada ProjectToken.
- El frontend debe consultar roles on-chain para mostrar/ocultar funciones admin.