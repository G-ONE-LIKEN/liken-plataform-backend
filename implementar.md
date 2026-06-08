# implementar.md — Hallazgos del escaneo profundo

> Auditoría de código de la plataforma LIKEN (backend microservicios + frontend Next.js + contratos Solidity + infra GKE).
> Fecha: 2026-06-08. Cada hallazgo incluye ubicación, impacto y propuesta.
> Prioridad: 🔴 Crítico · 🟠 Alto · 🟡 Medio · ⚪ Bajo / mejora.

---

## Resumen ejecutivo

| # | Severidad | Hallazgo |
|---|---|---|
| 1 | 🔴 | El gateway no hace *strip* de los headers `X-User-*` entrantes → spoofing de identidad en rutas públicas. |
| 2 | 🔴 | `blockchain-service` no puede desplegar contratos en GKE: el workspace de Foundry no está en la imagen ni montado. |
| 3 | 🔴 | `dividends.distributed` tiene consumidor pero **ningún productor** → las notificaciones de dividendos nunca se envían. |
| 4 | 🟠 | Mismatch de campos en el payload de `investment.token_purchased` → notificación de compra muestra `null`. |
| 5 | 🟠 | Sin `ErrorHandlingDeserializer` ni DLQ en ningún consumer → un mensaje malformado bloquea la partición (poison-pill). |
| 6 | 🟠 | Dual-write Kafka/DB dentro de `@Transactional` → eventos perdidos o fantasma sin patrón outbox. |
| 7 | 🟠 | `/api/wallets/deposit` acredita fondos arbitrarios sin pasarela de pago. |
| 8 | 🟠 | Clave privada del firmante on-chain en `Secret` plano de K8s / variable de entorno. |
| 9 | 🟡 | Access token JWT guardado en `localStorage` (expuesto a XSS). |
| 10 | 🟡 | `projects.state_changed` se produce pero no tiene consumidor (productor huérfano). |
| 11 | 🟡 | CORS con patrones `http://localhost:*` / `127.0.0.1:*` horneados también en producción. |
| 12 | 🟡 | Sin TLS: Ingress sirve HTTP plano → JWT y refresh cookie viajan en claro. |
| 13 | 🟡 | Todos los servicios en `replicas: 1` y sin `resources.requests/limits`. |
| 14 | 🟡 | Indexer blockchain single-instance sin lock: no escalable a >1 réplica. |
| 15 | ⚪ | Postgres/Kafka/Redis in-cluster con una sola réplica (SPOF). |
| 16 | ⚪ | Token JWT aceptado por query param `?access_token=` (se filtra en logs/proxies). |
| 17 | ⚪ | Eventos Kafka sin esquema compartido (Map<String,Object>) → mismatches silenciosos. |

---

## 🔴 Críticos

### 1. Spoofing de identidad: el gateway no limpia los headers `X-User-*` entrantes

**Ubicación:** `api-gateway/src/main/java/com/plataforma/gateway/filter/JwtAuthFilter.java:55` y `:87`; lado servicio en `*/security/GatewayHeaderAuthFilter.java`.

**Problema:** En rutas públicas (`GET /api/projects`, `GET /api/projects/{id}`, `/metrics`, y todas las `PUBLIC_PATHS`) el filtro hace `return chain.filter(exchange)` **sin eliminar** los headers `X-User-Id` / `X-User-Role` / `X-User-Permissions` que pudiera haber mandado el cliente. Los servicios backend confían en esos headers verbatim (`request.getHeader("X-User-Id")` en `GatewayHeaderAuthFilter`), construyendo el `SecurityContext` con el rol que diga el header. Un cliente puede enviar:

```
GET /api/projects  HTTP/1.1
X-User-Id: 1
X-User-Role: ADMIN
X-User-Permissions: project:create,project:update
```

y project-service lo tratará como ADMIN en cualquier lógica que dependa del rol sobre esos paths.

**Impacto:** Escalada de privilegios / suplantación. Aunque dentro del clúster los servicios son ClusterIP, la frontera de confianza (el gateway) no sanitiza el input del cliente, que es el vector real.

**Propuesta:** Al **inicio** de `JwtAuthFilter.filter`, eliminar incondicionalmente todos los headers de identidad de la request entrante, antes de decidir si es pública o no:

```java
ServerHttpRequest cleaned = request.mutate()
    .headers(h -> { h.remove("X-User-Id"); h.remove("X-User-Role");
                    h.remove("X-User-Permissions"); h.remove("X-User-Tier");
                    h.remove("X-User-KycStatus"); h.remove("X-Developer-Status"); })
    .build();
exchange = exchange.mutate().request(cleaned).build();
```

y recién después inyectar los valores derivados del JWT validado.

---

### 2. `blockchain-service` no puede desplegar contratos en producción (GKE)

**Ubicación:** `services/blockchain-service/Dockerfile`; `services/blockchain-service/src/main/java/com/plataforma/blockchain/service/ProjectPublicationService.java:73`; `infra/gcp-gke/manifests/overlays/prod/configmap.yaml:22`.

**Problema:** `ProjectPublicationService.deploy()` ejecuta `forge script` en el directorio `PUBLICATION_CONTRACTS_WORKSPACE` (default `/contracts`) y falla con *"No existe contractsWorkspace para deploy: /contracts"* si el directorio no existe. En local, `docker-compose.yml:424` monta `./contracts:/contracts`. Pero:
- El `Dockerfile` instala Foundry **pero no hace `COPY` del directorio `contracts/`**.
- El Deployment de `blockchain-service` en GKE **no monta ningún volumen** con los contratos.

→ La publicación on-chain de proyectos está rota en GKE.

**Impacto:** El flujo "aprobar proyecto → desplegar Offering" no funciona en producción. El proyecto queda en `OnChainStatus.FAILED`.

**Propuesta (elegir una):**
- **A (simple):** Agregar al Dockerfile `COPY ../../contracts /contracts` (requiere ajustar el build context al raíz del repo) o copiar `contracts/` dentro del módulo y `COPY`.
- **B:** Empaquetar los contratos compilados + scripts en un init-container o imagen base y montarlos como volumen en el Deployment.
- Verificar también que `forge` tenga las libs de `contracts/lib` (OpenZeppelin) disponibles en la imagen.

---

### 3. `dividends.distributed`: consumidor sin productor → notificaciones de dividendos nunca se envían

**Ubicación:** consumidor en `services/notification-service/.../consumer/KafkaConsumers.java:221`. Búsqueda global: ningún `kafkaTemplate.send("dividends.distributed", ...)` en el repo.

**Problema:** notification-service escucha `dividends.distributed`, pero ese topic **no lo publica nadie**. El `blockchain-service` emite `dividends.claimed` (al retirar) y `dividends.deposited` (al depositar). Resultado: la notificación "Dividendo acreditado" jamás se dispara.

**Impacto:** Funcionalidad de notificación de dividendos silenciosamente muerta.

**Propuesta:** Decidir la semántica correcta y alinear:
- Si "dividendo acreditado al usuario" = el holder reclamó → cambiar el listener a `dividends.claimed` (que sí trae `userId`, `amount`).
- O bien que `invest-dividend-service` republique un `dividends.distributed` enriquecido (con `projectId`) tras consumir `dividends.claimed`.

---

## 🟠 Altos

### 4. Payload mismatch en `investment.token_purchased` → notificación de compra con `null`

**Ubicación:** productor `services/blockchain-service/.../EventHandlerService.java:71` (`handleTokensPurchased`); consumidor `services/notification-service/.../KafkaConsumers.java:197` (`onTokenPurchased`).

**Problema:** El productor pone en el payload `usdcAmount`, `lknAmount`, `walletAddress`, `userId`, `offeringContractAddress` (sin `projectId`). El consumidor lee `payload.get("amount")`, `payload.get("tokens")` y `asLong(payload.get("projectId"))` — todos ausentes. La notificación queda: *"Tu inversión de **null** en el proyecto #**null** fue confirmada."*

**Impacto:** Notificación de compra inservible. Síntoma del problema #17 (eventos sin esquema).

**Propuesta:** Unificar nombres de campos. Como `projectId` no viaja en el log on-chain, el consumidor que lo necesita debe resolverlo por `offeringContractAddress` contra project-service, o el productor enriquecer el evento antes de publicarlo.

---

### 5. Consumers Kafka sin `ErrorHandlingDeserializer` ni DLQ (poison-pill)

**Ubicación:** `*/shared/config/KafkaConfig.java` (ej. `services/wallet-service/.../KafkaConfig.java:43`). Búsqueda global: 0 usos de `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`, `ErrorHandlingDeserializer`.

**Problema:** El `consumerFactory` usa `JsonDeserializer` desnudo. Un mensaje no deserializable lanza excepción en el poll loop; sin `ErrorHandlingDeserializer` el offset no avanza y el contenedor reintenta el mismo registro **indefinidamente**, bloqueando la partición. No hay DLQ, así que un fallo de lógica de negocio tampoco tiene destino de descarte/observabilidad. (El README de notification menciona tópicos `.dlq` que no existen.)

**Impacto:** Un solo evento corrupto puede congelar el consumo de toda una partición; pérdida silenciosa de eventos.

**Propuesta:** En cada servicio:
- Envolver el value deserializer con `ErrorHandlingDeserializer`.
- Registrar un `DefaultErrorHandler` con `DeadLetterPublishingRecoverer` (topic `<topic>.DLT`) y backoff acotado (p. ej. `FixedBackOff(1000L, 3)`).

---

### 6. Dual-write Kafka/DB sin outbox

**Ubicación:** múltiples `@Transactional` que publican a Kafka inline, p. ej. `invest-dividend-service/.../InvestmentService.java:99` (`tierPublisher.publish` dentro de `recordPurchase`), `user-service/.../WalletLinkingService.java`, `wallet-service/.../WalletService.java`.

**Problema:** El `kafkaTemplate.send(...)` ocurre dentro de la transacción de base de datos. Si la tx hace rollback luego del send → evento fantasma (consumidores reaccionan a algo que no se persistió). Si la tx commitea pero el broker está caído → evento perdido. No hay patrón transactional-outbox ni `@TransactionalEventListener(AFTER_COMMIT)`.

**Impacto:** Inconsistencias entre el estado persistido y los eventos publicados; difícil de diagnosticar.

**Propuesta:** Adoptar **transactional outbox** (tabla `outbox` escrita en la misma tx + relay que publica) o, como mínimo, publicar en `@TransactionalEventListener(phase = AFTER_COMMIT)` para garantizar que el evento solo sale si la tx commiteó. La idempotencia ya existente del lado consumidor cubre el at-least-once.

---

### 7. `/api/wallets/deposit` acredita dinero sin pasarela de pago

**Ubicación:** `services/wallet-service/.../controller/WalletController.java:54`.

**Problema:** Cualquier usuario autenticado puede `POST /api/wallets/deposit { amount }` y se le acredita ese saldo, sin ningún proveedor de pago, verificación ni límite. Es dinero ficticio.

**Impacto:** Si la wallet representa valor real (USDC, retiros), es una falla grave. Aceptable solo si es explícitamente un mock de MVP.

**Propuesta:** Documentarlo como mock o integrar una pasarela real (y mover la acreditación a un webhook firmado del proveedor, no a un endpoint del usuario). Como mínimo, deshabilitar `deposit` directo en producción.

---

### 8. Clave privada del firmante on-chain en Secret plano

**Ubicación:** `infra/gcp-gke/manifests/overlays/prod/secrets.example.yaml:31` (`PUBLICATION_SIGNER_PRIVATE_KEY`); consumida en `ProjectPublicationService.java:91` (pasada como env var al subproceso `forge`).

**Problema:** La clave que firma despliegues on-chain (controla fondos/roles) vive en un `Secret` Opaque de Kubernetes (base64, no cifrado en reposo por defecto) y se inyecta como variable de entorno.

**Impacto:** Cualquiera con acceso de lectura a Secrets del namespace, o que pueda volcar el entorno del pod, obtiene la clave que controla los contratos.

**Propuesta:** Mover a GCP Secret Manager / KMS (o un firmante remoto tipo KMS-backed signer). No exponerla como env var; leerla bajo demanda. Restringir RBAC de Kubernetes sobre Secrets.

---

## 🟡 Medios

### 9. Access token en `localStorage` (XSS)

**Ubicación:** `liken-plataform-frontend/shared/lib/api-client.ts:29-42, 71`.

**Problema:** El access token JWT se guarda en `localStorage` (`liken.session.token`). Cualquier XSS en la SPA puede exfiltrarlo. El refresh token sí está en cookie HttpOnly (correcto).

**Propuesta:** Mantener el access token solo en memoria (variable de módulo / contexto), reconstruyéndolo vía `/refresh` al recargar usando la cookie HttpOnly. Si se requiere persistencia, evaluar cookie HttpOnly también para el access token y dejar de leerlo en JS.

---

### 10. `projects.state_changed` producido pero sin consumidor

**Ubicación:** productores en `blockchain-service/.../EventHandlerService.java:145` y `project-service/.../ProjectEventPublisher.java:74`. Ningún `@KafkaListener("projects.state_changed")` en el repo.

**Problema:** Se publica el cambio de estado pero nadie lo consume (notification no lo escucha; project-service tampoco). Es trabajo y tráfico sin efecto, o falta el consumidor previsto (¿notificación al owner? ¿push SSE?).

**Propuesta:** Decidir: agregar el consumidor que faltaba (probablemente en notification-service para avisar al owner) o eliminar la publicación.

---

### 11. CORS con `localhost`/`127.0.0.1` horneado en producción

**Ubicación:** `api-gateway/.../config/CorsConfig.java:21`, `services/user-service/.../SecurityConfig.java:66`, `services/project-service/.../SecurityConfig.java:55`. Gateway default `app.frontend-url: http://localhost:*` (`application.yml:8`).

**Problema:** `setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", frontendUrl))` junto con `allowCredentials(true)` deja habilitados orígenes de desarrollo aun en prod.

**Propuesta:** Parametrizar la lista por entorno; en producción permitir solo el origen real. No mezclar `localhost:*` con `allowCredentials`.

---

### 12. Sin TLS en el Ingress

**Ubicación:** `infra/gcp-gke/manifests/base/networking.yaml` (Ingress GCE sin TLS); `infra/gcp-gke/README.md` (`FRONTEND_ORIGIN = http://34.160.119.148`).

**Problema:** El tráfico (incluyendo `Authorization: Bearer` y la cookie `refresh_token`) viaja en HTTP plano. Hay un `managed-certificate.yaml` en el repo pero sin dominio configurado.

**Propuesta:** Configurar dominio + `ManagedCertificate`, redirigir HTTP→HTTPS, y marcar la cookie de refresh como `Secure`.

---

### 13. `replicas: 1` y sin `resources` en todos los Deployments

**Ubicación:** `infra/gcp-gke/manifests/base/apps.yaml` (todos los Deployments).

**Problema:** Toda app corre con una sola réplica (cualquier rolling update o crash corta tráfico) y sin `resources.requests/limits` (el scheduler no puede planificar bien; riesgo de OOM/“ruidoso vecino”).

**Propuesta:** `replicas: 2` + `PodDisruptionBudget` en los servicios sin estado; definir requests/limits realistas; agregar `HorizontalPodAutoscaler`.

---

### 14. Indexer blockchain single-instance sin lock distribuido

**Ubicación:** `services/blockchain-service/.../indexer/BlockchainIndexer.java:40` (`@Scheduled`).

**Problema:** Con >1 réplica, ambas escanearían los mismos bloques (la idempotencia evita duplicados pero gasta RPC y puede competir por el checkpoint).

**Propuesta:** Lock distribuido (Redis `SET NX` / ShedLock) o particionado por contrato. Mientras tanto, fijar explícitamente `replicas: 1` y documentarlo.

---

## ⚪ Bajos / mejoras

### 15. Postgres / Kafka / Redis in-cluster con una réplica (SPOF)
`infra/gcp-gke/manifests/base/infra.yaml`. Para una primera versión está bien; migrar Postgres a **Cloud SQL**, Kafka a un servicio gestionado/operador y Redis a Memorystore para HA y backups.

### 16. JWT por query param `?access_token=`
`api-gateway/.../JwtAuthFilter.java:108`. Necesario para `EventSource` (SSE no permite headers), pero los query strings se filtran a access logs y proxies. Mitigar con tokens de vida corta específicos para el stream o moviendo el SSE detrás de un endpoint que setee cookie.

### 17. Eventos Kafka sin contrato de esquema
Los payloads son `Map<String,Object>` sin tipos compartidos. Esto causó los mismatches #3, #4 y el huérfano #10. **Propuesta:** definir DTOs de evento compartidos (módulo `contracts`/`events` común) o un schema registry (Avro/JSON Schema) para que productor y consumidor no se desincronicen en silencio.

### 18. `deletion_protection = false` en el cluster GKE
`infra/gcp-gke/terraform/main.tf:94`. Activar en producción para evitar borrados accidentales del cluster.

### 19. `OfferingContract.refund()` depende de allowance del treasury
`contracts/src/OfferingContract.sol:247` (`usdc.safeTransferFrom(treasury, msg.sender, amount)`). Los reembolsos requieren que el `treasury` mantenga balance y allowance hacia el Offering; si no, los refunds revierten. Documentar/operacionalizar esa precondición.

---

## Lo que está bien (para no romperlo)

- **Contratos Solidity sólidos:** `ReentrancyGuard` + patrón CEI + `SafeERC20` + `AccessControl` por roles en `OfferingContract`, `DividendDistributor`, `LinkenToken`, `ProjectRegistry`. El enum `Stage` (FUNDING/ACTIVE/PAUSED) coincide con el mapeo del indexer.
- **Firmas de eventos** del indexer coinciden con las declaraciones Solidity (no hay topics mal calculados).
- **Idempotencia** consistente: `processed_event` / `external_event_id UNIQUE` / `eventId = txHash:logIndex`.
- **Workload Identity** para GCS en vez de claves de service account.
- **Reconciliación de actividad on-chain huérfana** por wallet (patrón bien resuelto para la race Web3).
- **Indexer con checkpoints + confirmaciones** para resistir reorgs y reanudar tras restart.
- **Separación gateway/servicios** con identidad por headers (una vez corregido el #1).

---

## Orden sugerido de ataque

1. **#1** (strip de headers) — cambio chico, cierra una escalada de privilegios.
2. **#2** (contratos en la imagen) — desbloquea el flujo on-chain en prod.
3. **#3 / #4** (topics y payloads de dividendos/compras) — arreglan funcionalidad muerta.
4. **#5 / #6** (robustez Kafka: DLQ + outbox) — evitan pérdida de datos.
5. **#8 / #12** (clave privada + TLS) — endurecimiento de producción.
6. Resto según roadmap.

---
---

# Segunda ronda de auditoría (2026-06-08)

Foco: validación de firmas, upload de archivos, autorización, paginación, lógica financiera de los contratos y frontend. Numeración continúa desde la primera ronda.

## Resumen

| # | Severidad | Hallazgo |
|---|---|---|
| 19 | 🔴 (escalado) | El `OfferingContract` **no escrowa el USDC**: `buy()` lo envía al treasury y `refund()` lo recupera de ahí → si la ronda falla, los reembolsos dependen de la honestidad y liquidez del treasury. |
| 20 | 🟠 | Paginación sin tope en `GET /api/wallets/me/movements` (`size` controlado por el cliente, sin máximo) → DoS / agotamiento de memoria. |
| 21 | 🟠 | Upload de KYC sin allowlist de tipo/extensión: confía en el `Content-Type` del cliente y no sanitiza el nombre de archivo. |
| 22 | 🟡 | El nonce de vinculación de wallet vive en memoria (`ConcurrentHashMap`) → se rompe con >1 réplica de user-service. |
| 23 | 🟡 | Endpoints `/internal/**` sin autenticación alguna (ni secreto compartido): un foothold en el cluster puede forjar callbacks (ej. `publication-success`) o leer datos internos. |
| 24 | 🟡 | Verificación del `idToken` de Google sin chequeo de `iss`. |
| 25 | ⚪ | `/actuator/**` con `permitAll` (mitigado hoy por `exposure=health,info`, pero el patrón es amplio). |

---

## 🔴 Escalado

### 19 (revisado a crítico). El OfferingContract no mantiene el USDC en escrow

**Ubicación:** `contracts/src/OfferingContract.sol:195` (`buy`) y `:247` (`refund`).

**Problema:** Durante la ronda, cada `buy()` transfiere el USDC del inversor **directamente al `treasury`** (`usdc.safeTransferFrom(msg.sender, treasury, usdcAmount)`). El LKN sí queda escrowado en el contrato, pero el USDC no. Si la ronda **falla** (no se alcanza el soft cap al vencer el deadline), `refund()` intenta `usdc.safeTransferFrom(treasury, msg.sender, amount)` — es decir, **tira de los fondos desde el treasury**, lo que exige que el treasury (a) todavía tenga el USDC y (b) haya dado allowance al contrato.

**Impacto:** Se pierde la garantía que justifica el soft cap: en una ronda fallida, si el treasury gastó/movió los fondos o no fijó allowance, los reembolsos revierten y **los inversores pierden su dinero**. El "escrow" es de confianza, no trustless.

**Propuesta:** Que `buy()` mantenga el USDC en el propio `OfferingContract`. Liberar al treasury recién en `finalize()` (éxito); en fallo, `refund()` paga desde el balance del contrato. Es el patrón estándar de crowdsale con soft cap.

---

## 🟠 Altos

### 20. Paginación sin límite en movimientos de wallet

**Ubicación:** `services/wallet-service/.../controller/WalletController.java:40-48` → `PageRequest.of(page, size)` con `@RequestParam(defaultValue = "20") int size`.

**Problema:** El `size` lo arma el controller a mano desde el query param, sin tope. `GET /api/wallets/me/movements?size=1000000` se honra y carga un millón de filas en memoria. (Los endpoints que usan `@PageableDefault` quedan acotados por el máximo de Spring —2000 por defecto—, pero este no.)

**Propuesta:** Cap explícito (`Math.min(size, 100)`) o migrar a `Pageable` resuelto con `spring.data.web.pageable.max-page-size`.

### 21. Upload de KYC sin validación de tipo de archivo

**Ubicación:** `services/user-service/.../kyc/service/KycService.java:73-78`.

**Problema:** Se valida que la cantidad de archivos matchee la de tipos, pero **no** se restringe el tipo/MIME/extensión: se acepta cualquier archivo y se usa el `Content-Type` que envía el cliente (`file.getContentType()`) como metadato del objeto en GCS. Además el `getOriginalFilename()` se concatena al nombre del objeto sin sanitizar. Hay límite de 10 MB (multipart), pero no de contenido.

**Impacto:** Subida de ejecutables/HTML/SVG con scripts como "documento KYC"; si esos archivos luego se sirven o se abren desde el panel admin, riesgo de XSS/entrega de malware. Nombre de archivo no sanitizado.

**Propuesta:** Allowlist de MIME/extensión (`application/pdf`, `image/png`, `image/jpeg`), validar los magic bytes (no solo el header del cliente), normalizar/descartar el filename del usuario y servir siempre con `Content-Disposition: attachment`.

---

## 🟡 Medios

### 22. Nonce de vinculación de wallet en memoria → no soporta múltiples réplicas

**Ubicación:** `services/user-service/.../service/WalletLinkingService.java:57` (`ConcurrentHashMap<Long, NonceEntry>`).

**Problema:** El nonce de `/me/wallet/nonce` se guarda en memoria del proceso. Con >1 réplica de user-service, el `POST /nonce` puede caer en la réplica A y el `POST /wallet` en la B (que no tiene el nonce) → la vinculación falla de forma intermitente. El propio código lo documenta como pendiente ("mover a Redis").

**Impacto:** Correctitud al escalar. Hoy funciona solo porque hay 1 réplica (ver #13).

**Propuesta:** Mover el nonce a Redis con TTL (ya está disponible en la infra).

> ✔️ Por lo demás, la verificación de firma está **bien hecha**: `ecrecover` EIP-191, nonce de un solo uso con TTL de 5 min, address en checksum EIP-55 y rechazo de wallet ya vinculada a otro usuario.

### 23. Endpoints `/internal/**` sin autenticación ni secreto compartido

**Ubicación:** `*/SecurityConfig.java` (`requestMatchers("/internal/**").permitAll()`), p. ej. project/user/invest.

**Problema:** El modelo de confianza ([ADR-0005](docs/adr/ADR-0005-Aislamiento-de-red-servicios-internos-ClusterIP)) descansa 100% en el aislamiento de red (ClusterIP). Los `/internal/**` no tienen ninguna autenticación: ni JWT, ni mTLS, ni un header de secreto compartido. Un atacante con un foothold en el cluster (otro pod comprometido) puede, por ejemplo, `POST /internal/projects/publication-success` y marcar proyectos como desplegados con addresses arbitrarias, o leer `/internal/users/{id}/context`.

**Propuesta:** Defensa en profundidad: agregar un secreto compartido entre servicios (header firmado / token de servicio) o NetworkPolicies que restrinjan qué pods pueden hablar con cada `/internal`. mTLS (service mesh) es la opción fuerte si el modelo de amenazas lo justifica.

### 24. Verificación de Google `idToken` sin chequeo de `iss`

**Ubicación:** `services/auth-service/.../service/GoogleTokenService.java:38-43`.

**Problema:** Se valida `aud == clientId` y `email_verified` contra el endpoint `tokeninfo`, pero no se verifica `iss ∈ {accounts.google.com, https://accounts.google.com}`. El endpoint `tokeninfo` solo valida tokens de Google (mitiga el riesgo), pero el chequeo explícito de `iss` es parte de la validación recomendada por Google.

**Propuesta:** Agregar la verificación de `iss`. Idealmente migrar a la librería oficial `google-api-client` (`GoogleIdTokenVerifier`), que valida firma, `aud`, `iss` y `exp` localmente sin llamar a `tokeninfo` por request.

---

## ⚪ Bajos

### 25. `/actuator/**` con `permitAll`
`services/wallet-service/.../SecurityConfig.java:29` (y patrón similar en otros). Hoy el riesgo es bajo porque `management.endpoints.web.exposure.include=health,info`. Pero el matcher es amplio: si alguien expone más endpoints (`env`, `mappings`, `heapdump`) quedarían públicos. Restringir el `permitAll` a `/actuator/health` y `/actuator/info`.

---

## Verificado y correcto en esta ronda (no tocar)

- **Method security activa:** `@EnableMethodSecurity` presente en los 5 servicios que usan `@PreAuthorize` (los `@PreAuthorize` no se ignoran silenciosamente).
- **Checks de ownership** en project-service (`if (!isAdmin && !project.getOwnerId().equals(requesterId))`).
- **Reporte admin de wallet** protegido con `@PreAuthorize("hasRole('ADMIN')")`.
- **Validación de montos:** `@NotNull` + `@DecimalMin("0.01")` en depósito y retiro (sin negativos/cero).
- **Firma de wallet linking:** `ecrecover` EIP-191 correcto, nonce de un solo uso (ver #22 solo por el almacenamiento).
- **SSE:** `SseEmitterRegistry` limpia los emitters en `onCompletion`/`onTimeout`/`onError` (sin leak).
- **Frontend:** sin `dangerouslySetInnerHTML` ni sinks de XSS evidentes; sin logging de credenciales en backend.
- **Límite de multipart:** 10 MB en user-service (acota el tamaño, aunque falta el tipo — ver #21).
