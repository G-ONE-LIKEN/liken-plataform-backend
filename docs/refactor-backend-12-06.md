# Refactor backend — 12/06/2026

Registro detallado de todos los cambios aplicados en la sesión del 12/06.
Cada sección indica el commit, los archivos tocados, el problema que resolvía
y cómo se verificó. Los ADR citados amplían el razonamiento y las alternativas
descartadas.

## Resumen de commits

| Commit | Repo | Contenido |
|---|---|---|
| `d9b7bc1` | frontend | CI: rollout restart del frontend aunque el tag no cambie |
| `f9a2c77` | frontend | Soporte `NEXT_PUBLIC_API_URL=/` (requests same-origin) |
| `0f9959e` | frontend | Redirect 301 `liken.lat` → `www.liken.lat` |
| `6c3d9a9` | frontend | Fixes de auditoría en componentes de inversión |
| `54d32fa` | frontend | README real del proyecto |
| `f926097` | backend | Contracts horneados en la imagen del blockchain-service |
| `3e02cc6` | backend | **Sprint 1** — Resiliencia (ADR-0023) |
| `972eb95` | backend | **Sprint 2** — Mensajería confiable (ADR-0024) |
| `ca33edf` | backend | **Sprint 3** — Observabilidad (ADR-0025) |
| `bfc457f` | backend | **Sprint 4** — Hardening (ADR-0026) + fix tests project-service |
| `bf6eb4e` | backend | **Sprint 5** — Persistencia y backups (ADR-0027) |
| `20edddb` | backend | READMEs actualizados |

---

## 0. Contexto previo: incidente de login en producción

Antes del refactor se diagnosticó y arregló el "Failed to fetch" del login en
`liken.lat`:

- **Causa raíz**: el bundle del frontend tenía horneado
  `NEXT_PUBLIC_API_URL=https://34.160.119.148` (IP del LB). El certificado TLS
  solo es válido para `liken.lat`/`www.liken.lat` → todo fetch moría en el
  handshake. La GitHub Variable ya estaba corregida, pero el re-run del
  workflow reutilizaba el mismo commit → mismo tag → `kubectl set image`
  era un no-op y los pods nunca tomaban la imagen nueva.
- **Fixes**:
  - `d9b7bc1`: el workflow agrega `kubectl rollout restart deployment/frontend`
    después del `set image` — el redeploy ocurre aunque el tag no cambie.
  - `f9a2c77` (`shared/config/env.ts`): `NEXT_PUBLIC_API_URL=/` significa
    same-origin — el front usa rutas relativas `/api/...` y el ingress enruta
    al gateway bajo el mismo dominio. El CORS deja de existir como modo de
    fallo, entres por `liken.lat` o `www.liken.lat`.
  - `0f9959e` (`next.config.ts`): redirect 301 por host del dominio sin www
    al canónico (el LB de GCE no soporta redirects por host).

---

## 1. Publicación on-chain rota en GKE (`f926097`)

**Problema**: el blockchain-service ejecuta `forge script` contra el workspace
`/contracts` para deployar el OfferingContract de cada proyecto. Ese directorio
solo existía como **volumen de docker-compose** (`./contracts:/contracts`) —
en GKE el pod no tenía nada montado ahí, así que toda publicación fallaba.

**Cambios**:
- `services/blockchain-service/Dockerfile`: el build pasa a usar **contexto en
  la raíz del repo** y copia `contracts/` (src, scripts, `foundry.toml`,
  `remappings.txt` y las libs vendoreadas de OpenZeppelin/forge-std — 1017
  archivos commiteados, sin submodules) a `/contracts` dentro de la imagen.
- `.github/workflows/build-push-backend.yml`: la entrada de blockchain-service
  en la matriz usa `context: .` + `dockerfile:` explícito; el paso de build
  acepta el campo `dockerfile` con fallback al comportamiento anterior para
  los demás servicios.
- `docker-compose.yml`: mismo contexto raíz; el volumen local sigue pisando la
  copia para iterar contratos sin rebuild.
- `.dockerignore` en la raíz: excluye `.git`, `**/target`, `logs`,
  `contracts/{out,cache,broadcast,docs}` del contexto.

**Verificación**: build local de la imagen + `forge build` ejecutado adentro
del contenedor (compiló los 4 contratos, exit 0).

---

## 2. Sprint 1 — Resiliencia en llamadas síncronas (`3e02cc6`, ADR-0023)

**Problema**: el gateway llamaba a user-service en cada request autenticado
sin timeout ni circuit breaker, y auth-service usaba `new RestTemplate()`
pelado. Un user-service colgado agotaba los pools de threads y tumbaba TODA la
plataforma. Además, cualquier falla se reportaba como `401 Usuario no
encontrado` — un error engañoso.

**api-gateway**:
- `pom.xml`: `spring-cloud-starter-circuitbreaker-reactor-resilience4j`.
- `config/ResilienceConfig.java` (nuevo): circuit breaker `user-context` —
  TimeLimiter 3s, ventana de 10 llamadas, abre al 50% de fallas, half-open
  automático a los 15s, **ignora `WebClientResponseException.NotFound`** (un
  404 es respuesta de negocio, no falla del servicio).
- `service/UserContextService.java`: la llamada de contexto corre dentro del
  breaker (`ReactiveCircuitBreaker.run`), sin fallback — el error llega al
  filtro, que decide.
- `filter/JwtAuthFilter.java`: distingue `404 → 401 "usuario no encontrado"`
  de cualquier otra falla `→ 503 "servicio temporalmente no disponible"`.
  Helper `reject(exchange, status, message)` extraído.
- `filter/JwtAuthFilterTest.java`: el test que esperaba 401 ante falla
  genérica se separó en dos casos (404→401, infra→503).

**auth-service**:
- `pom.xml`: `resilience4j-spring-boot3` 2.2.0 + `spring-boot-starter-aop`.
- `shared/config/AppConfig.java`: RestTemplate vía builder con timeouts
  (conexión 2s, lectura 3s) — antes era infinito.
- `shared/client/UserServiceClient.java`: `@CircuitBreaker(name = "user-service")`
  a nivel clase.
- `shared/exception/GlobalExceptionHandler.java`: `CallNotPermittedException`
  (breaker abierto), `ResourceAccessException` (timeout/conexión) y
  `HttpServerErrorException` → `503` con mensaje claro.
- `application.properties`: config del breaker (ignora `HttpClientErrorException`
  — los 4xx no abren el breaker), health indicator y exposición de
  `/actuator/circuitbreakers` y `metrics`.

**notification-service**:
- `client/UserServiceClient.java`: timeouts en el RestClient (3s/5s) — era el
  último cliente interno sin límite. (invest, wallet, blockchain y project ya
  los tenían vía RestTemplateBuilder.)

**Extras**: `scripts/chaos-demo.ps1` — demo reproducible: apaga user-service,
muestra que las rutas públicas siguen vivas y las autenticadas responden 503
en milisegundos, lo restaura y muestra la recuperación automática.

**Verificación**: tests de gateway (24, con Redis en Docker) y auth verdes.

---

## 3. Sprint 2 — Mensajería confiable (`972eb95`, ADR-0024)

**Problema 1 — eventos perdibles**: 8 consumers (5 en wallet, 3 en invest)
envolvían su lógica en `try/catch + log.error` → el offset se commiteaba
igual y el evento se perdía para siempre (p. ej. una compra on-chain que
llegaba mientras project-service estaba caído).

**Problema 2 — indexer at-most-once**: `KafkaEventPublisher` publicaba
fire-and-forget y el checkpoint avanzaba aunque Kafka no confirmara.

**Problema 3 — deadlock del refund**: on-chain, `RoundFailed` solo se emite
con el **primer** `refund()`, pero la UI muestra el botón de refund solo
cuando el backend ya marcó la ronda FAILED (que ocurría solo al consumir ese
evento). Nadie podía iniciar el refund desde la UI.

**Cambios**:
- **Retries + DLT** en invest-dividend (`shared/config/KafkaErrorHandlingConfig.java`,
  nuevo), wallet y project (en sus `KafkaConfig`): `DefaultErrorHandler` con
  `FixedBackOff(2s, 3)` + `DeadLetterPublishingRecoverer` hacia `<topic>.DLT`
  en partición 0 fija (el DLT se auto-crea con 1 partición). En wallet el
  handler se enchufa a la factory custom; en invest/project Spring Boot lo
  toma solo.
- **8 consumers destapados** (se eliminó el try/catch; la excepción ES el
  mecanismo): `TokensPurchasedConsumer`, `DividendsClaimedConsumer`,
  `WalletLinkedConsumer` (invest); `TokenPurchasedConsumer`,
  `DividendDistributedConsumer`, `WalletRefundConsumer`, `WalletLinkedConsumer`,
  `OrderMatchedConsumer` (wallet). En `TokensPurchasedConsumer`, el caso
  "projectId no resoluble" pasó de descartar con warn a lanzar
  `IllegalStateException` → retries → DLT.
- **`KafkaEventPublisher`**: `send().get(10s)` — si Kafka no confirma, la
  transacción rollbackea y el indexer NO avanza el checkpoint → el bloque se
  reescanea (at-least-once; los duplicados los absorbe la idempotencia por
  `eventId` que ya existía en ambos extremos).
- **`RoundExpirationMonitor`** (blockchain-service, nuevo): cada 60s consulta
  por `eth_call` (`state/deadline/totalRaised/softCap`) los Offerings listados
  por project-service; si una ronda OPEN venció sin soft cap, publica un
  `projects.round_failed` **sintético** con `eventId = expired:<address>` —
  la dedupe del publisher garantiza una sola emisión. El RefundCard aparece
  solo, sin esperar el primer refund on-chain.
- **notification-service queda best-effort a propósito** (sin DLT): un email
  fallido no debe frenar ni reintentar el pipeline.
- `docs/eventos-kafka.md` (nuevo): esquema canónico de cada topic, garantías
  de entrega y cómo inspeccionar/reprocesar un DLT.

**Verificación**: compilan los 4 servicios; tests verdes en invest, wallet y
blockchain. Las 13 fallas de project-service se verificaron **preexistentes**
corriendo los mismos tests contra HEAD sin los cambios.

---

## 4. Sprint 3 — Observabilidad (`ca33edf`, ADR-0025)

**Problema**: imposible responder "¿qué pasó con el request X?" — sin
identificador común entre servicios. Además, los appenders a archivo
(`/app/logs`) causaban los `FileNotFoundException` visibles en Error Reporting
y duplicaban lo que Cloud Logging ya captura.

**Tracing (los 8 servicios)**:
- `micrometer-tracing-bridge-brave` en los 8 poms (insertado tras el bloque de
  actuator, que todos tenían).
- Propagación W3C automática por HTTP (WebClient/RestTemplate vía builder) y
  **por Kafka**: `spring.kafka.template/listener.observation-enabled=true` en
  los servicios Boot-managed (gateway, invest, project, user, blockchain) y
  `setObservationEnabled(true)` en las factories custom de wallet y
  notification.
- Sampling 100% (`management.tracing.sampling.probability=1.0`).

**Logs**:
- `traceId` agregado al patrón de consola de los 7 `logback-spring.xml`
  (`%X{traceId:-}`); el JSON ya incluía `<mdc/>`.
- **Perfil `k8s`** en los 7 logbacks: appenders a archivo solo en `!k8s`;
  en `k8s` un único `CONSOLE_JSON` con campo `severity` (Cloud Logging lo
  parsea nativo). `SPRING_PROFILES_ACTIVE=k8s` agregado al configmap de prod.
- Los 6 `emptyDir` de logs eliminados de `apps.yaml` (se borraban en cada
  reinicio: peso muerto).

**Métricas**:
- `kafka.dlt.messages{topic}`: contador incrementado en el destination
  resolver de los 3 error handlers — cualquier valor > 0 es una falla real
  pendiente de reproceso.
- `indexer.lag.blocks` (gauge en `BlockchainIndexer`): bloques entre el head
  confirmado y el checkpoint más atrasado.
- `/actuator/metrics` expuesto en todos los servicios.

**Verificación**: 8 servicios compilan; suites verdes en gateway (con Redis),
auth, wallet, invest y blockchain. XML de los logbacks validado.

---

## 5. Sprint 4 — Hardening (`bfc457f`, ADR-0026)

**Hallazgo 1 — spoofing de identidad**: el gateway inyectaba `X-User-*` tras
validar el JWT pero NO eliminaba los que venían del cliente; en rutas públicas
la request pasaba tal cual → `X-User-Role: ADMIN` forjado llegaba al servicio
downstream como identidad válida.
- `JwtAuthFilter`: **strip incondicional** de los 6 headers de identidad al
  inicio del filtro, para toda request. Test de regresión
  (`publicPath_forgedIdentityHeaders_areStripped`).

**Hallazgo 2 — contraseña en texto plano en Redis**: el registro pendiente de
verificación serializaba el `RegisterRequest` completo a Redis por 10 minutos.
- `EmailVerificationService` (auth): valida fortaleza sobre el texto plano,
  hashea con BCrypt y persiste el hash; el DTO lleva `passwordEncoded=true`.
- `UserService` + `UserInternalController` + DTO (user-service): nueva variante
  `registerVerifiedLocalUser(user, role, passwordEncoded)` que con el flag NO
  re-hashea ni re-valida fortaleza (un hash BCrypt no es validable).

**Hallazgo 3 — cookies frágiles**: la cookie de refresh usaba la API de
`jakarta.servlet.Cookie` (sin SameSite) con un hack que reescribía el header
`Set-Cookie` a mano, y sin flag `Secure`.
- `AuthController`: `ResponseCookie` con HttpOnly + SameSite=Lax + `Secure`
  configurable (`auth.cookie-secure`, `AUTH_COOKIE_SECURE=true` en el
  configmap de prod, `false` en dev local HTTP).

**Hallazgo 4 — secretos commiteados**: `secrets.yaml` de prod (JWT secret,
API key de Resend, private key del signer, seeds de admin) estaba en el repo.
- `secrets.yaml` y `rendered/` (100% generado por el render script) removidos
  del tracking + gitignore. Verificado que `kustomization.yaml` no los
  referencia → el CI no cambia.
- ⚠️ **Los valores siguen en el historial de git**: la mitigación real es la
  **rotación** (JWT secret, key de Resend, wallet nueva para el signer,
  passwords de seeds) — tarea operativa pendiente.

**Decisión de alcance**: el módulo compartido `liken-shared` se **difirió**
con justificación en el ADR — exige migrar los 8 builds a multi-módulo
(Dockerfiles + CI), es un sprint propio.

**Además**: este commit incluyó el fix de los 13 tests desactualizados de
`ProjectControllerTest`/`ProjectServiceImplTest` (tarea derivada de la
auditoría).

**Verificación**: gateway (tests incl. strip), auth y la suite completa de
user-service (Testcontainers) verdes; project-service pasó de 13 fallas a
todo verde.

---

## 6. Sprint 5 — Persistencia y backups (`bf6eb4e`, ADR-0027)

**Problema**: estado que no sobrevivía a eventos rutinarios de Kubernetes.
Kafka y Zookeeper en disco efímero (un re-schedule perdía topics, offsets y
eventos no consumidos), Postgres sin backups ni `Recreate`, Redis efímero
(cada reinicio deslogueaba a todos).

**Cambios** (`infra/gcp-gke/manifests/base/`):
- `infra.yaml`:
  - **Kafka**: PVC 10Gi + `KAFKA_LOG_DIRS=/var/lib/kafka/data` +
    `fsGroup: 1000` (la imagen Confluent corre como appuser) + `Recreate`.
  - **Zookeeper**: PVC 2Gi en `/var/lib/zookeeper/data` + `fsGroup` +
    `Recreate`. Va junto con el de Kafka: si ZK pierde su data y Kafka no,
    el broker no arranca (cluster ID mismatch).
  - **Postgres**: `strategy: Recreate` (RWO + rolling update = deadlock).
  - **Redis**: `--appendonly yes` + PVC 1Gi en `/data` + `Recreate`.
- `backup-cronjob.yaml` (nuevo): CronJob diario (06:00 UTC = 03:00 AR) —
  initContainer `postgres:15-alpine` hace `pg_dumpall | gzip` a un emptyDir;
  container `google-cloud-cli` lo sube a `gs://<bucket>/backups/` usando la
  ServiceAccount `liken-storage-access` (Workload Identity, sin keys).
- `docs/runbook-backups.md` (nuevo): backup a demanda, restore desde dump,
  restore desde snapshot del PD, snapshot schedule (gcloud) y reconstrucción
  de proyecciones re-indexando la chain.

**Nota de primer deploy**: Kafka/ZK arrancan con volúmenes nuevos vacíos —
los topics se recrean solos y los consumers parten de cero (idempotencia
mediante). Corte breve del pipeline de eventos durante el rollout.

**Verificación**: YAML bien formado + `kubectl kustomize` renderiza el base
completo sin errores.

---

## 7. Frontend — fixes de auditoría (`6c3d9a9`)

- **Side effects en render** (`buy-lkn-flow.tsx`, `claim-dividends-card.tsx`,
  `refund-card.tsx`): `invalidateQueries()/refetch()` post-confirmación de tx
  corrían en cada render → loop de invalidaciones con React Query. Movidos a
  `useEffect` con deps `[txConfirmed, txHash]`.
- **Gate de KYC fail-closed** (`buy-lkn-flow.tsx`): `user?.kycStatus !== "APPROVED"`
  — antes el truthy-check salteaba el gate cuando `kycStatus` venía `undefined`.
- **Claim seguro** (`claim-dividends-card.tsx`): botón deshabilitado si la
  wallet conectada en MetaMask ≠ la vinculada (el pendiente mostrado es de la
  vinculada; firmar con otra revertía on-chain con `DD: nothing to claim`).

**Verificación**: dev server arranca limpio y las 3 páginas que usan los
componentes (`/projects/[id]`, `/dashboard/investments`, `/dashboard/wallet`)
compilan y devuelven 200 sin errores de consola.

---

## 8. Documentación (`20edddb` + `54d32fa`)

- ADRs nuevos: 0023 (resiliencia), 0024 (mensajería confiable), 0025
  (observabilidad), 0026 (hardening), 0027 (persistencia) + índice actualizado.
- `docs/eventos-kafka.md`, `docs/runbook-backups.md`,
  `docs/plan-mejoras-arquitectura.md` (plan de los 5 sprints con el guion de
  demos para la defensa).
- READMEs actualizados: raíz, api-gateway, auth, blockchain, wallet, invest,
  infra/gcp-gke (valores de FRONTEND_ORIGIN y NEXT_PUBLIC_API_URL corregidos),
  y el del frontend reescrito (era el boilerplate de create-next-app).

---

## Pendientes operativos (manuales, fuera del código)

1. **Rotación de secretos** (urgente — siguen en el historial de git):
   `JWT_SECRET`, `MAIL_PASSWORD` (Resend), `PUBLICATION_SIGNER_PRIVATE_KEY`
   (generar wallet nueva y fondearla con SepoliaETH), `APP_SEED_ADMIN_PASSWORD_*`.
2. Snapshot schedule del Persistent Disk de Postgres (comandos en el runbook).
3. Lifecycle rule de 30 días en el bucket para `/backups`.
4. Aumento de quota de CPUs en `us-central1` (bloquea los node upgrades de GKE).
5. Uptime check sobre `https://www.liken.lat/api/projects` + alertas sobre
   `kafka.dlt.messages` e `indexer.lag.blocks`.

## Cómo desplegar todo

```powershell
cd C:\Users\Gonza\programacion\SIP\liken-plataform-backend ; git push origin main
cd C:\Users\Gonza\programacion\SIP\liken-plataform-frontend ; git push origin master
```
