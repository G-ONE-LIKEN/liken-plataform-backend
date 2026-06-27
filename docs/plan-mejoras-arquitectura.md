# Plan de mejoras de arquitectura — LIKEN

> Proyecto académico — Seminario de Integración Profesional.
> Objetivo: demostrar prácticas profesionales de ingeniería sobre una
> plataforma de microservicios + blockchain ya funcional en GKE.
>
> Cada mejora indica **qué demuestra** (el argumento para la defensa),
> esfuerzo estimado, archivos a tocar y criterio de aceptación verificable.

---

## Sprint 1 — Resiliencia entre servicios ⭐ (mayor relación costo/demostración)

**Qué demuestra:** que el sistema tolera fallas parciales — el argumento
central de por qué microservicios. Sin esto, una caída de user-service
tumba toda la plataforma vía el gateway.

### 1.1 Timeouts + Circuit Breakers en clientes internos
- Agregar `resilience4j-spring-boot3` a los servicios con clientes HTTP
  internos: api-gateway (`UserContextService`), auth-service
  (`UserServiceClient`, `NotificationServiceClient`), invest-dividend
  (`ProjectClient`, `UserContextClient`), wallet (`UserContextClient`),
  blockchain (`ProjectServiceClient`, `UserLookupClient`).
- Configurar: timeout de conexión 2s / lectura 3s, circuit breaker
  (ventana 10 llamadas, abre al 50% de fallas, half-open a los 15s),
  retry solo en GET idempotentes (2 reintentos, backoff 200ms).
- Fallbacks explícitos: gateway responde 503 "servicio degradado" (no 401);
  invest descarta y reintenta vía DLQ (ver Sprint 2); notification omite
  el envío.

### 1.2 Demo de caos para la defensa
- Script `scripts/chaos-demo.ps1`: `kubectl scale deployment/user-service
  --replicas=0` → mostrar que login devuelve 503 inmediato (no timeout de
  30s), que las rutas públicas siguen vivas, y que el circuit breaker se
  recupera solo al restaurar el servicio.

**Aceptación:** con user-service caído, `GET /api/projects` responde < 1s
y `POST /api/auth/login` responde 503 < 3s. Métricas del breaker visibles
en `/actuator/circuitbreakers`.

**Esfuerzo:** 2-3 días.

---

## Sprint 2 — Mensajería confiable (Kafka con garantías)

**Qué demuestra:** consistencia eventual bien hecha — eventos financieros
que no se pierden. Es la respuesta a "¿y si se cae el consumidor?"

### 2.1 Retries + Dead Letter Topics
- `DefaultErrorHandler` con `DeadLetterPublishingRecoverer` en el
  `KafkaConfig` de cada servicio consumidor (notification, invest-dividend,
  wallet, project, user): 3 reintentos con backoff exponencial → si falla,
  el evento va a `<topic>.DLT` con headers de diagnóstico.
- Eliminar los `try/catch + log.error` que hoy tragan excepciones en los
  consumers (`TokensPurchasedConsumer`, `OrderMatchedConsumer`, etc.) —
  dejar que la excepción dispare el error handler.

### 2.2 Publicación at-least-once en el indexer
- `KafkaEventPublisher.publish` → `send().get()` (confirmación síncrona)
  antes de que `BlockchainIndexer` avance el checkpoint. La idempotencia
  por `eventId` ya absorbe duplicados.

### 2.3 Eventos tipados
- Reemplazar `Map<String, Object>` por DTOs en los consumers (los DTOs ya
  existen en wallet-service como modelo a seguir). Documentar el esquema
  canónico de cada topic en `docs/eventos-kafka.md` (extiende ADR-0012).

### 2.4 Scheduler de rondas vencidas (bug funcional encontrado en auditoría)
- `@Scheduled` en blockchain-service: para cada offering OPEN, `eth_call`
  a `deadline()/totalRaised()/softCap()`; si venció sin soft cap, publicar
  `projects.round_failed` sintético (eventId `expired:{address}`).
- Cierra el deadlock: hoy el botón de refund nunca aparece porque el evento
  on-chain `RoundFailed` recién se emite con el primer refund.

**Aceptación:** matar project-service, generar una compra on-chain,
verificar que el evento queda en reintentos y se procesa al restaurar;
forzar un evento venenoso y verlo en el DLT. Ronda con deadline vencido
muestra el RefundCard sin intervención manual.

**Esfuerzo:** 3-4 días.

---

## Sprint 3 — Observabilidad

**Qué demuestra:** operabilidad — poder responder "¿qué pasó con el
request X?" en un sistema distribuido.

### 3.1 Tracing distribuido
- Micrometer Tracing + bridge OTel en todos los servicios; propagación
  W3C `traceparent` (el gateway lo genera, WebClient/RestClient lo
  propagan, Kafka lo lleva en headers).
- En GCP: export a Cloud Trace (integración nativa, sin collector).

### 3.2 Logs correlacionados
- `traceId` en el patrón de log (MDC) — los logs JSON que ya tienen pasan
  a ser correlacionables en Cloud Logging.
- De paso: perfil `k8s` con appenders solo-consola (elimina los
  FileNotFoundException de /app/logs vistos en Error Reporting y los
  emptyDir de logs).

### 3.3 Métricas de negocio + alertas
- Micrometer: `indexer_lag_blocks`, `kafka_dlt_messages`,
  `publication_failures`. Alerta en Cloud Monitoring si el indexer se
  atrasa > 100 bloques o aparece un mensaje en DLT.
- Uptime check de GCP sobre `https://www.liken.lat/api/projects`.

**Aceptación:** desde un login en el front, encontrar en Cloud Trace el
span gateway→auth→user con el mismo traceId que aparece en los logs.

**Esfuerzo:** 2-3 días.

---

## Sprint 4 — Calidad de código y seguridad

**Qué demuestra:** mantenibilidad y madurez de ingeniería.

### 4.1 Módulo compartido `liken-shared`
- Módulo Maven con: `ApiResponse`, `GatewayHeaderAuthFilter`,
  `SecurityConfig` base, `Auditable`, jerarquía de excepciones,
  `GlobalExceptionHandler`, DTOs de eventos Kafka (de 2.3).
- Elimina ~30 archivos duplicados en 7 servicios y la divergencia ya
  existente (project-service tiene su propia copia desviada).

### 4.2 Hardening (hallazgos de la auditoría de seguridad)
- Gateway: **strip de headers `X-User-*` entrantes** en todas las rutas
  (hoy spoofeables en rutas públicas) — 5 líneas en `JwtAuthFilter`.
- Header `X-Internal-Token` compartido para `/internal/**` (defensa en
  profundidad sobre el aislamiento de red).
- Secrets: mover a GCP Secret Manager + External Secrets Operator; rotar
  JWT secret, API key de Resend y private key del signer (están
  commiteados); sacar `.env` y `secrets.yaml` del repo + gitignore.
- BCrypt del password **antes** de guardar el registro pendiente en Redis.
- Cookies con flag `Secure` + `ResponseCookie` (reemplaza el hack de
  Set-Cookie manual en AuthController).

### 4.3 Fixes de frontend (auditoría)
- `useEffect` para invalidaciones post-tx (hoy corren en render) en
  `buy-lkn-flow`, `claim-dividends-card`, `refund-card`.
- Gate KYC estricto (`kycStatus !== "APPROVED"`, no truthy-check).
- Deshabilitar claim si la wallet conectada ≠ vinculada.

**Aceptación:** `mvn verify` verde en todos los servicios usando el módulo
compartido; request con `X-User-Role: ADMIN` forjado a ruta pública llega
al servicio sin ese header; trufflehog/gitleaks sobre el repo sin hallazgos.

**Esfuerzo:** 4-5 días (lo más largo es el módulo compartido + rotación).

---

## Sprint 5 — Infraestructura confiable (GCP)

**Qué demuestra:** que el despliegue es producción-grade, no un demo frágil.

- **PVC para Kafka** (+ `KAFKA_LOG_DIRS`) — hoy un re-schedule del pod
  pierde todos los eventos. Alternativa con más puntos: migrar a KRaft
  (elimina Zookeeper).
- **Backups de Postgres**: snapshot schedule del Persistent Disk +
  CronJob de `pg_dump` a GCS. `strategy: Recreate` en el Deployment
  (RWO + rolling update puede deadlockear).
- **Redis con AOF** (PVC chico) — que un reinicio no desloguee a todos.
- **Aumento de quota** para habilitar node upgrades de GKE.
- Documentar runbook de restore (probarlo una vez: ese es el entregable).

**Aceptación:** `kubectl delete pod kafka-xxx` y los topics sobreviven;
restore de un dump en una DB limpia documentado con captura.

**Esfuerzo:** 2 días.

---

## Backlog documentado (no implementar — vale como ADRs para la defensa)

Demostrar criterio también es decidir qué NO hacer y por qué:

- **ADR: separación de credenciales auth/user** — hoy el hash de password
  viaja entre servicios; la separación correcta es cirugía mayor. Documentar
  el trade-off y el plan de migración.
- **ADR: dividendos por proyecto vs token global** — la dilución actual
  sobre el supply total (incluye escrows) es una decisión económica a
  revisar antes de mainnet.
- **ADR: marketplace P2P con settlement on-chain** — diseño ya esbozado
  (escrow de órdenes en contrato, matching off-chain solo como proyección),
  reemplaza el enfoque del ADR-0014.
- **ADR: SSE multi-réplica** — Redis pub/sub cuando notification-service
  escale más allá de 1 réplica.
- **Contratos v2** — rescate de LKN atrapados, burns notificados al
  distributor, escrow real de USDC para refunds, whitelist KYC on-chain.

---

## Orden y dependencias

```
Sprint 1 (resiliencia)  ──┐
Sprint 2 (Kafka)         ──┼──▶ Sprint 3 (observabilidad: mide lo anterior)
                           │
Sprint 4 (shared lib usa DTOs de 2.3; hardening independiente)
Sprint 5 (infra: independiente, puede ir en paralelo con todo)
```

Para la presentación final, el guion natural es:
1. Demo de caos (Sprint 1) — "el sistema degrada, no colapsa".
2. Evento venenoso → DLT → reproceso (Sprint 2) — "no perdemos dinero".
3. Un trace de punta a punta en Cloud Trace (Sprint 3) — "sabemos qué pasa".
4. Mostrar el diff del módulo compartido y el reporte de gitleaks (Sprint 4).
5. Matar el pod de Kafka en vivo (Sprint 5) — "los datos sobreviven".
```
