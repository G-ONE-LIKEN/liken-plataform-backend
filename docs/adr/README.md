# Architecture Decision Records (ADR)

Registro de decisiones de arquitectura de la plataforma LIKEN. Cada ADR documenta una decisión técnica: contexto, qué se decidió, alternativas descartadas y consecuencias.

## Índice

| ADR | Título | DD original | Estado |
|-----|--------|-------------|--------|
| [ADR-0001](ADR-0001-Optimizacion-Memoria-JVM-Contenedores-Docker) | Optimización de memoria JVM en contenedores Docker | — | Aceptado |
| [ADR-0002](ADR-0002-Healthcheck-Zookeeper-HTTP-AdminServer) | Healthcheck de Zookeeper vía HTTP AdminServer | — | Aceptado |
| [ADR-0003](ADR-0003-Infraestructura-Kubernetes-en-la-nube) | Infraestructura: Kubernetes en la nube | DD001 | Aceptado |
| [ADR-0004](ADR-0004-Validacion-JWT-centralizada-en-el-gateway) | Validación de JWT centralizada en el API Gateway | DD002 | Aceptado |
| [ADR-0005](ADR-0005-Aislamiento-de-red-servicios-internos-ClusterIP) | Aislamiento de red: servicios internos no públicos | DD003 | Aceptado |
| [ADR-0006](ADR-0006-JWT-contiene-solo-userId) | El JWT contiene solo userId; permisos en tiempo real | DD004 | Aceptado |
| [ADR-0007](ADR-0007-Cache-de-contexto-de-usuario-en-el-gateway) | Cache de contexto de usuario en el gateway (TTL 30s) | DD005 | Aceptado |
| [ADR-0008](ADR-0008-auth-service-como-microservicio-independiente) | auth-service como microservicio independiente | DD006 | Aceptado |
| [ADR-0009](ADR-0009-Headers-de-identidad-inyectados-por-el-gateway) | Headers de identidad inyectados por el gateway | DD007 | Aceptado |
| [ADR-0010](ADR-0010-docker-compose-unico-en-la-raiz) | docker-compose único en la raíz del repo | DD008 | Aceptado |
| [ADR-0011](ADR-0011-Rate-limiting-en-el-gateway-con-Redis) | Rate limiting en el API Gateway con Redis | DD009 | Aceptado |
| [ADR-0012](ADR-0012-Modelo-canonico-de-eventos-Kafka) | Modelo canónico de eventos Kafka | DD010 | Aceptado (parcial) |
| [ADR-0013](ADR-0013-Bounded-context-de-invest-dividend-service) | Bounded context y modelo de invest-dividend-service | DD011 | Revisado |
| [ADR-0014](ADR-0014-Matching-engine-de-marketplace-service) | Matching engine de marketplace-service | DD012 | Propuesto |
| [ADR-0015](ADR-0015-Tiers-de-usuario-y-KYC) | Tiers de usuario y KYC en user-service | DD013 | Aceptado (con desviaciones) |
| [ADR-0016](ADR-0016-Cloud-provider-GCP-y-storage-en-GCS) | Cloud provider GCP y storage en GCS | DD014 | Aceptado |
| [ADR-0017](ADR-0017-Modelo-de-integracion-on-chain-Web2-Web3) | Modelo de integración on-chain (la cadena como fuente de verdad) | — | Aceptado |
| [ADR-0018](ADR-0018-Indexador-de-eventos-on-chain) | Indexador de eventos on-chain (checkpoints + confirmaciones) | — | Aceptado |
| [ADR-0019](ADR-0019-Publicacion-de-contratos-via-Foundry) | Publicación de contratos vía Foundry (`forge`) | — | Aceptado (con desviación) |
| [ADR-0020](ADR-0020-Reconciliacion-de-actividad-on-chain-huerfana) | Reconciliación de actividad on-chain huérfana por wallet | — | Aceptado |
| [ADR-0021](ADR-0021-Estrategia-de-sesion-y-vinculacion-de-wallet) | Estrategia de sesión y vinculación de wallet por firma | — | Aceptado |
| [ADR-0022](ADR-0022-Unidades-y-precision-monetaria-on-chain-off-chain) | Unidades y precisión monetaria on-chain ↔ off-chain | — | Aceptado |
| [ADR-0023](ADR-0023-Resiliencia-en-llamadas-sincronas-internas) | Resiliencia en llamadas síncronas internas (timeouts + circuit breakers) | — | Aceptado |
| [ADR-0024](ADR-0024-Mensajeria-confiable-retries-DLT-y-eventos-sinteticos) | Mensajería confiable: retries, DLT y eventos sintéticos | — | Aceptado |
| [ADR-0025](ADR-0025-Observabilidad-tracing-logs-correlacionados-y-metricas) | Observabilidad: tracing, logs correlacionados y métricas | — | Aceptado |
| [ADR-0026](ADR-0026-Hardening-identidad-secretos-y-credenciales) | Hardening: headers de identidad, secretos y credenciales | — | Aceptado |
| [ADR-0027](ADR-0027-Persistencia-y-backups-del-plano-de-datos) | Persistencia y backups del plano de datos en GKE | — | Aceptado |

## Convenciones

- **Nombre de archivo:** `ADR-NNNN-titulo-en-kebab-case` (sin extensión, igual que los ADR existentes).
- **Numeración:** incremental y nunca se reutiliza. Un ADR no se borra: se marca `Reemplazado por ADR-XXXX`.
- **Estados:** `Propuesto` · `Aceptado` · `Revisado` · `Reemplazado` · `Descartado`.
- **DD original:** los ADR-0003 a ADR-0016 formalizan las "Decisiones de Diseño" (DD001–DD014) que el código referencia como `DDxxx`. El número DD se conserva como alias para no romper esas referencias. Fuente histórica: [`docs/LISTO/decisiones-de-diseno.md`](../LISTO/decisiones-de-diseno.md).
- **ADR derivados del código:** los ADR-0017 a 0022 no tienen DD original — documentan decisiones de arquitectura que estaban implementadas en el código pero no escritas en ningún lado (principalmente la integración on-chain, la estrategia de sesión y el manejo de precisión monetaria).
- **ADR de robustez operacional:** los ADR-0023 a 0027 surgen del plan de mejoras de arquitectura ([`docs/plan-mejoras-arquitectura.md`](../plan-mejoras-arquitectura.md)): resiliencia, mensajería confiable, observabilidad, hardening y persistencia.
- **Estado de implementación:** cada ADR derivado de un DD incluye una sección con el estado real al 2026-06-08 y las desviaciones detectadas respecto de lo decidido (ver [`implementar.md`](../../implementar.md)).
