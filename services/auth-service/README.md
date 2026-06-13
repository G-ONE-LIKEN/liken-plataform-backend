# auth-service

Microservicio de autenticación de la plataforma LIKEN. Emite y gestiona tokens JWT. Es el único servicio que conoce las credenciales de los usuarios.

## Responsabilidades

- **Login**: verifica credenciales y emite un access token JWT (15 min) + un refresh token HttpOnly en cookie (7 días).
- **Login con Google**: valida el `idToken` de Google OAuth y emite la misma pareja access+refresh (provisiona el usuario en `user-service` si es la primera vez).
- **Registro con verificación de email**: `register/request` dispara el envío de un código; el alta real se confirma con `email-verification/confirm`.
- **Verificación de email**: solicitar / confirmar / reenviar código (con TTL, cooldown y máximo de intentos).
- **Refresh con rotación**: emite un nuevo access token y rota el refresh token (revoca el viejo, emite uno nuevo).
- **Logout**: revoca el refresh token y limpia la cookie.
- **Cambio de contraseña**: verifica la actual y actualiza la nueva.
- **Comunicación interna** con `user-service` (datos de usuario) y `notification-service` (envío de códigos de verificación).

No tiene base de datos relacional propia: usa **Redis** para refresh tokens y códigos de verificación de email.

## Por qué es un servicio separado

Ver [ADR-0008](../../docs/adr/ADR-0008-auth-service-como-microservicio-independiente). Resumen: auth tiene un ciclo de vida y responsabilidades distintos al CRUD de usuarios. Separarlo permite escalar, auditar y evolucionar la estrategia de autenticación de forma independiente.

## Dominio

```
com.plataforma
├── auth/
│   ├── controller/AuthController.java
│   ├── service/
│   │   ├── AuthService.java                 # login, google, refresh, logout, change-password
│   │   ├── EmailVerificationService.java    # registro + verificación de email (Redis)
│   │   ├── RefreshTokenService.java         # emisión/rotación/revocación (Redis)
│   │   └── GoogleTokenService.java          # valida idToken de Google
│   └── dto/...                              # Login/Register/Google/EmailVerification requests
└── shared/
    ├── client/UserServiceClient.java        # HTTP interno a user-service
    ├── client/NotificationServiceClient.java# envío de códigos vía notification-service
    ├── config/AppConfig.java                # RestTemplate, PasswordEncoder
    ├── config/SecurityConfig.java           # Stateless, todo público (sin filtro JWT)
    ├── exception/...
    └── security/JwtUtils.java               # generateToken (login emite JWT)
```

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| JWT | JJWT 0.11.5 (HS256) |
| Estado | Redis 7 (refresh tokens + códigos de verificación) |
| OAuth | Google Identity (validación de `idToken`) |
| HTTP cliente | RestTemplate (Spring Web) |
| Tests | JUnit 5 + Mockito |

## Endpoints

| Método | Ruta | JWT | Descripción |
|--------|------|-----|-------------|
| POST | `/api/auth/login` | No | Credenciales → access token (body) + refresh token (cookie). |
| POST | `/api/auth/google` | No | `idToken` de Google → access + refresh. |
| POST | `/api/auth/register/request` | No | Inicia registro: envía código de verificación al email. |
| POST | `/api/auth/email-verification/request` | No | Solicita un código de verificación. |
| POST | `/api/auth/email-verification/confirm` | No | Confirma el email con el código. |
| POST | `/api/auth/email-verification/resend` | No | Reenvía el código (respeta cooldown). |
| POST | `/api/auth/refresh` | No (usa cookie) | Renueva el access token y rota el refresh token. |
| POST | `/api/auth/logout` | No (usa cookie) | Revoca el refresh token y limpia la cookie. |
| POST | `/api/auth/change-password` | Sí | Cambia la contraseña del usuario autenticado. |

### Login
```json
POST /api/auth/login
{ "email": "user@mail.com", "password": "secreto" }

→ 200 { "data": { "accessToken": "<jwt>" }, ... }   + Set-Cookie: refresh_token=...; HttpOnly; SameSite=Lax[; Secure]
→ 401 si credenciales inválidas o cuenta inactiva
→ 503 si user-service no está disponible (circuit breaker, ADR-0023)
```

El access token dura 15 min (`jwt.expiration-ms=900000`); el refresh token, 7 días. El frontend renueva vía `/refresh` usando la cookie. El flag `Secure` se activa con `AUTH_COOKIE_SECURE=true` (prod sobre HTTPS).

> **Registro pendiente seguro (ADR-0026):** el registro en dos pasos guarda el
> formulario en Redis hasta confirmar el código de email — con la contraseña
> **ya hasheada con BCrypt** (la fortaleza se valida sobre el texto plano antes
> de hashear). user-service recibe `passwordEncoded=true` y no re-hashea.
> Ninguna contraseña toca Redis en texto plano.

### Cambio de contraseña
```json
POST /api/auth/change-password
X-User-Id: 42                    # inyectado por el gateway (ADR-0004)
{ "oldPassword": "actual", "newPassword": "nueva" }

→ 200 si exitoso
→ 401 si contraseña actual incorrecta
```

> El gateway valida el JWT antes de rutear y inyecta `X-User-Id`. auth-service confía en ese header y no parsea el token.

## Comunicación interna con user-service

| Llamada | Endpoint |
|---------|----------|
| Buscar usuario por email (login) | `GET user-service /internal/users/by-email/{email}` |
| Buscar usuario por ID (changePassword) | `GET user-service /internal/users/{id}` |
| Actualizar contraseña | `PUT user-service /internal/users/{id}/password` |
| Marcar email verificado | `PUT user-service /internal/users/{id}/email-verified` |
| Provisionar / login Google | `POST user-service /internal/users/google` |
| Alta tras verificación | `POST user-service /internal/users` |
| Enviar código de verificación | `POST notification-service /internal/emails/transactional` |

Los endpoints `/internal/**` no llevan JWT — están protegidos a nivel de red (ClusterIP en Kubernetes, ver ADR-0005).

Todas las llamadas a user-service tienen **timeouts (2s/3s) y circuit breaker** (`@CircuitBreaker("user-service")`, Resilience4j): si user-service falla repetidamente, el breaker abre y auth responde `503` inmediato en vez de agotar el pool de threads (ADR-0023). Estado visible en `/actuator/circuitbreakers`.

## Variables de entorno

| Variable | Descripción | Default desarrollo |
|----------|-------------|-------------------|
| `PORT` | Puerto del servicio | `8081` |
| `JWT_SECRET` | Clave HS256 (mínimo 256 bits) | — |
| `REDIS_HOST` / `REDIS_PORT` | Redis (refresh tokens + códigos de verificación) | `localhost` / `6379` |
| `USER_SERVICE_URL` | URL interna de user-service | `http://localhost:8080` |
| `NOTIFICATION_SERVICE_URL` | URL interna de notification-service (envío de códigos) | `http://localhost:8087` |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth (validación del `idToken`) | (vacío) |
| `EMAIL_VERIFICATION_TTL_MINUTES` | Vigencia del código | `10` |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS` | Cooldown entre reenvíos | `60` |
| `EMAIL_VERIFICATION_MAX_ATTEMPTS` | Intentos máximos por código | `5` |
| `AUTH_COOKIE_SECURE` | Flag `Secure` de la cookie de refresh (`true` en prod/HTTPS) | `false` |

El `JWT_SECRET` debe ser **idéntico** al configurado en `api-gateway` (que valida los tokens emitidos por este servicio).

## División de responsabilidades de auth

| Quién | Responsabilidad |
|-------|----------------|
| `auth-service` | Verifica credenciales (login) y **emite** JWTs |
| `api-gateway` | **Valida** JWTs en todas las requests posteriores (ADR-0004) |
| Servicios backend | Confían en los headers `X-User-Id`, `X-User-Role`, `X-User-Permissions` inyectados por el gateway |

`auth-service` no valida JWTs entrantes: el gateway ya lo hizo antes de rutear la request a este servicio.

## Tests

```bash
mvn test   # Unit tests (Mockito, sin Spring context completo)
```
