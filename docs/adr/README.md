# Architecture Decision Records (ADR)

Registro de decisiones de arquitectura de la plataforma LIKEN. Cada ADR documenta una decision técnica: contexto, qué se decidio, alternativas descartadas y consecuencias.

## indice

| ADR | Titulo | DD original | Estado |
|-----|--------|-------------|--------|
| [ADR-0001](ADR-0001-Optimizacion-Memoria-JVM-Contenedores-Docker) | Optimizacion de memoria JVM en contenedores Docker | — | Aceptado |
| [ADR-0002](ADR-0002-Healthcheck-Zookeeper-HTTP-AdminServer) | Healthcheck de Zookeeper via HTTP AdminServer | — | Aceptado |
| [ADR-0003](ADR-0003-Infraestructura-Kubernetes-en-la-nube) | Infraestructura: Kubernetes en la nube | DD001 | Aceptado |
| [ADR-0004](ADR-0004-Validacion-JWT-centralizada-en-el-gateway) | Validacion de JWT centralizada en el API Gateway | DD002 | Aceptado |
| [ADR-0005](ADR-0005-Aislamiento-de-red-servicios-internos-ClusterIP) | Aislamiento de red: servicios internos no publicos | DD003 | Aceptado |
| [ADR-0006](ADR-0006-JWT-contiene-solo-userId) | El JWT contiene solo userId; permisos en tiempo real | DD004 | Aceptado |
| [ADR-0007](ADR-0007-Cache-de-contexto-de-usuario-en-el-gateway) | Cache de contexto de usuario en el gateway (TTL 30s) | DD005 | Aceptado |
| [ADR-0008](ADR-0008-auth-service-como-microservicio-independiente) | auth-service como microservicio independiente | DD006 | Aceptado |
| [ADR-0009](ADR-0009-Headers-de-identidad-inyectados-por-el-gateway) | Headers de identidad inyectados por el gateway | DD007 | Aceptado |
| [ADR-0010](ADR-0010-docker-compose-unico-en-la-raiz) | docker-compose unico en la raiz del repo | DD008 | Aceptado |
| [ADR-0011](ADR-0011-Rate-limiting-en-el-gateway-con-Redis) | Rate limiting en el API Gateway con Redis | DD009 | Aceptado |
| [ADR-0012](ADR-0012-Modelo-canonico-de-eventos-Kafka) | Modelo canonico de eventos Kafka | DD010 | Aceptado (parcial) |
| [ADR-0013](ADR-0013-Bounded-context-de-invest-dividend-service) | Bounded context y modelo de invest-dividend-service | DD011 | Revisado |
| [ADR-0014](ADR-0014-Matching-engine-de-marketplace-service) | Matching engine de marketplace-service | DD012 | Propuesto |
| [ADR-0015](ADR-0015-Tiers-de-usuario-y-KYC) | Tiers de usuario y KYC en user-service | DD013 | Aceptado (con desviaciones) |
| [ADR-0016](ADR-0016-Cloud-provider-GCP-y-storage-en-GCS) | Cloud provider GCP y storage en GCS | DD014 | Aceptado |
| [ADR-0017](ADR-0017-Modelo-de-integracion-on-chain-Web2-Web3) | Modelo de integracion on-chain (la cadena como fuente de verdad) | — | Aceptado |
| [ADR-0018](ADR-0018-Indexador-de-eventos-on-chain) | Indexador de eventos on-chain (checkpoints + confirmaciones) | — | Aceptado |
| [ADR-0019](ADR-0019-Publicacion-de-contratos-via-Foundry) | Publicacion de contratos via Foundry (`forge`) | — | Aceptado (con desviacion) |
| [ADR-0020](ADR-0020-Reconciliacion-de-actividad-on-chain-huerfana) | Reconciliacion de actividad on-chain huérfana por wallet | — | Aceptado |
| [ADR-0021](ADR-0021-Estrategia-de-sesion-y-vinculacion-de-wallet) | Estrategia de sesion y vinculacion de wallet por firma | — | Aceptado |
| [ADR-0022](ADR-0022-Unidades-y-precision-monetaria-on-chain-off-chain) | Unidades y precision monetaria on-chain ↔ off-chain | — | Aceptado |

## Convenciones

- **Nombre de archivo:** `ADR-NNNN-titulo-en-kebab-case` (sin extension, igual que los ADR existentes).
- **Numeracion:** incremental y nunca se reutiliza. Un ADR no se borra: se marca `Reemplazado por ADR-XXXX`.
- **Estados:** `Propuesto` · `Aceptado` · `Revisado` · `Reemplazado` · `Descartado`.
- **DD original:** los ADR-0003 a ADR-0016 formalizan las "Decisiones de Diseño" (DD001–DD014) que el codigo referencia como `DDxxx`. El numero DD se conserva como alias para no romper esas referencias. Fuente historica: [`docs/LISTO/decisiones-de-diseno.md`](../LISTO/decisiones-de-diseno.md).
- **ADR derivados del codigo:** los ADR-0017 en adelante no tienen DD original — documentan decisiones de arquitectura que estaban implementadas en el codigo pero no escritas en ningun lado (principalmente la integracion on-chain, la estrategia de sesion y el manejo de precision monetaria).
- **Estado de implementacion:** cada ADR derivado de un DD incluye una seccion con el estado real al 2026-06-08 y las desviaciones detectadas respecto de lo decidido (ver [`implementar.md`](../../implementar.md)).
