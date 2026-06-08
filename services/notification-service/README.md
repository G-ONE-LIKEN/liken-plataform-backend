# notification-service

Microservicio de notificaciones de la plataforma LIKEN. Reacciona a eventos Kafka del sistema y genera **notificaciones in-app** (persistidas en `notification_db`), **emails transaccionales** (vía Resend SMTP + Thymeleaf) y **push en tiempo real** al frontend (Server-Sent Events).

## Responsabilidades

- **Consumir eventos Kafka** y, según el tipo, crear notificación in-app y/o enviar email.
- **Persistir** cada notificación en la tabla `notification` (sirve de historial y de soporte para el badge de no leídas).
- **Push en tiempo real** vía SSE: cuando se crea una notificación, se emite al instante a las conexiones abiertas de ese usuario.
- **Emails transaccionales** renderizando templates Thymeleaf y enviándolos por Resend.
- **Broadcast** administrativo a una audiencia (todos, admins, developers, inversores o un usuario puntual).
- **Idempotencia**: cada notificación generada por un evento usa un `eventId` estable (derivado del recurso) para no duplicar.

## Endpoints

### Públicos (vía gateway, autenticado)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/notifications?unread=false` | Lista paginada de notificaciones del usuario. |
| GET | `/api/notifications/unread-count` | Cantidad de no leídas (para el badge). |
| POST | `/api/notifications/{id}/read` | Marca una notificación como leída. |
| POST | `/api/notifications/read-all` | Marca todas como leídas. |
| GET | `/api/notifications/stream` | Stream SSE (`text/event-stream`). **Sin rate-limit** en el gateway por ser conexión de larga duración. |
| POST | `/api/notifications/broadcast` | **Solo ADMIN.** Envía un mensaje a una audiencia. |

### Internos (red privada, sin JWT)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/internal/emails/transactional` | Envía un email transaccional (`to`, `subject`, `templateName`, `variables`). Lo usan otros servicios. |

### Operativos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/actuator/health` | Healthcheck. |

## Eventos Kafka consumidos

| Tópico | Notificación generada |
|---|---|
| `projects.pending_approval` | A los admins: "Nuevo proyecto pendiente de aprobación". |
| `projects.approved` | Al owner: "Tu proyecto fue aprobado" (+ email). |
| `projects.rejected` | Al owner: "Tu proyecto fue rechazado" (+ email, con motivo). |
| `user.registered` | Al usuario: bienvenida in-app + email (usa el email del payload para evitar la race con la tx de registro). |
| `user.developer_registered` | A los admins: "Nuevo developer esperando verificación". |
| `user.developer_status_changed` | Al usuario: aprobado/rechazado como developer (+ email). |
| `investment.token_purchased` | Al inversor: "Compra de tokens confirmada" (+ email). |
| `dividends.distributed` | Al inversor: "Dividendo acreditado" (+ email). |
| `wallet.credited` | Al usuario: "Depósito recibido". |
| `wallet.debited` | Al usuario: "Retiro procesado". |

`NotificationType`: `USER_WELCOME`, `ADMIN_DEVELOPER_PENDING`, `ADMIN_PROJECT_PENDING`, `DEVELOPER_STATUS_CHANGED`, `PROJECT_APPROVED`, `PROJECT_REJECTED`, `INVESTMENT_CONFIRMED`, `DIVIDEND_RECEIVED`, `WALLET_FUNDED`, `WALLET_DEBITED`, `BROADCAST`.

> **Nota:** el consumer de dividendos escucha `dividends.distributed`. El `blockchain-service` emite `dividends.claimed` / `dividends.deposited`; verificá que exista un servicio que republique `dividends.distributed` o esas notificaciones no se disparan.

## Templates de email

Thymeleaf en `src/main/resources/templates/`:

```
templates/
├── welcome.html              # bienvenida (user.registered)
├── notification.html         # template genérico para emails transaccionales
└── email-verification.html   # verificación de email
```

## Configuración

| Env | Default | Descripción |
|---|---|---|
| `PORT` | `8087` | Puerto HTTP. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/notification_db` | Postgres del servicio. |
| `DB_USERNAME` / `DB_PASSWORD` | — | Credenciales Postgres. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker Kafka. |
| `MAIL_HOST` | `smtp.resend.com` | SMTP de Resend. |
| `MAIL_PORT` | `465` | Puerto SMTP (SSL directo). |
| `MAIL_USERNAME` | — | Usuario SMTP (`resend`). |
| `MAIL_PASSWORD` | — | API key de Resend. |
| `MAIL_AUTH` / `MAIL_STARTTLS` / `MAIL_SSL` | `true`/`false`/`true` | Flags SMTP. |
| `MAIL_FROM` | `onboarding@resend.dev` | Remitente. |
| `MAIL_FROM_NAME` | `LIKEN` | Nombre del remitente. |
| `USER_SERVICE_URL` | `http://localhost:8080` | Resuelve audiencias del broadcast y destinatarios admin. |
| `FRONTEND_URL` | `http://localhost:3000` | Base para los links en los emails. |

> El timeout de Tomcat está en `10m` para sostener las conexiones SSE; el gateway debe respetar ese timeout en la ruta `/api/notifications/stream`.

## Stack

Spring Boot 3.2.4 / Java 21 / PostgreSQL + Flyway / Kafka (consumer) / Spring Mail + Resend / Thymeleaf / SSE.
