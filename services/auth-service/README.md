# auth-service

Microservicio de autenticacion de la plataforma LIKEN. Emite y gestiona tokens JWT. Es el unico servicio que conoce las credenciales de los usuarios.

## Responsabilidades

- **Login**: verifica credenciales y emite un access token JWT (15 min) + un refresh token HttpOnly en cookie (7 dias).
- **Login con Google**: valida el `idToken` de Google OAuth y emite la misma pareja access+refresh (provisiona el usuario en `user-service` si es la primera vez).
- **Registro con verificacion de email**: `register/request` dispara el envio de un codigo; el alta real se confirma con `email-verification/confirm`.
- **Verificacion de email**: solicitar / confirmar / reenviar codigo (con TTL, cooldown y maximo de intentos).
- **Refresh con rotacion**: emite un nuevo access token y rota el refresh token (revoca el viejo, emite uno nuevo).
- **Logout**: revoca el refresh token y limpia la cookie.
- **Cambio de contraseña**: verifica la actual y actualiza la nueva.
- **Comunicacion interna** con `user-service` (datos de usuario) y `notification-service` (envio de codigos de verificacion).

No tiene base de datos relacional propia: usa **Redis** para refresh tokens y codigos de verificacion de email.

## Por que es un servicio separado

Ver [ADR-0008](../../docs/adr/ADR-0008-auth-service-como-microservicio-independiente). Resumen: auth tiene un ciclo de vida y responsabilidades distintos al CRUD de usuarios. Separarlo permite escalar, auditar y evolucionar la estrategia de autenticacion de forma independiente.

## Dominio

```
com.plataforma
├── auth/
│   ├── controller/AuthController.java
│   ├── service/
│   │   ├── AuthService.java                 # login, google, refresh, logout, change-password
│   │   ├── EmailVerificationService.java    # registro + verificacion de email (Redis)
│   │   ├── RefreshTokenService.java         # emision/rotacion/revocacion (Redis)
│   │   └── GoogleTokenService.java          # valida idToken de Google
│   └── dto/...                              # Login/Register/Google/EmailVerification requests
└── shared/
    ├── client/UserServiceClient.java        # HTTP interno a user-service
    ├── client/NotificationServiceClient.java# envio de codigos via notification-service
    ├── config/AppConfig.java                # RestTemplate, PasswordEncoder
    ├── config/SecurityConfig.java           # Stateless, todo publico (sin filtro JWT)
    ├── exception/...
    └── security/JwtUtils.java               # generateToken (login emite JWT)
```

## Stack

| Capa | Tecnologia |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| JWT | JJWT 0.11.5 (HS256) |
| Estado | Redis 7 (refresh tokens + codigos de verificacion) |
| OAuth | Google Identity (validacion de `idToken`) |
| HTTP cliente | RestTemplate (Spring Web) |
| Tests | JUnit 5 + Mockito |

## Endpoints

| Metodo | Ruta | JWT | Descripcion |
|--------|------|-----|-------------|
| POST | `/api/auth/login` | No | Credenciales → access token (body) + refresh token (cookie). |
| POST | `/api/auth/google` | No | `idToken` de Google → access + refresh. |
| POST | `/api/auth/register/request` | No | Inicia registro: envia codigo de verificacion al email. |
| POST | `/api/auth/email-verification/request` | No | Solicita un codigo de verificacion. |
| POST | `/api/auth/email-verification/confirm` | No | Confirma el email con el codigo. |
| POST | `/api/auth/email-verification/resend` | No | Reenvia el codigo (respeta cooldown). |
| POST | `/api/auth/refresh` | No (usa cookie) | Renueva el access token y rota el refresh token. |
| POST | `/api/auth/logout` | No (usa cookie) | Revoca el refresh token y limpia la cookie. |
| POST | `/api/auth/change-password` | Si | Cambia la contraseña del usuario autenticado. |

### Login
```json
POST /api/auth/login
{ "email": "user@mail.com", "password": "secreto" }

→ 200 { "data": { "accessToken": "<jwt>" }, ... }   + Set-Cookie: refresh_token=...; HttpOnly; SameSite=Lax
→ 401 si credenciales invalidas o cuenta inactiva
```

El access token dura 15 min (`jwt.expiration-ms=900000`); el refresh token, 7 dias. El frontend renueva via `/refresh` usando la cookie.

### Cambio de contraseña
```json
POST /api/auth/change-password
X-User-Id: 42                    # inyectado por el gateway (ADR-0004)
{ "oldPassword": "actual", "newPassword": "nueva" }

→ 200 si exitoso
→ 401 si contraseña actual incorrecta
```

> El gateway valida el JWT antes de rutear y inyecta `X-User-Id`. auth-service confia en ese header y no parsea el token.

## Comunicacion interna con user-service

| Llamada | Endpoint |
|---------|----------|
| Buscar usuario por email (login) | `GET user-service /internal/users/by-email/{email}` |
| Buscar usuario por ID (changePassword) | `GET user-service /internal/users/{id}` |
| Actualizar contraseña | `PUT user-service /internal/users/{id}/password` |
| Marcar email verificado | `PUT user-service /internal/users/{id}/email-verified` |
| Provisionar / login Google | `POST user-service /internal/users/google` |
| Alta tras verificacion | `POST user-service /internal/users` |
| Enviar codigo de verificacion | `POST notification-service /internal/emails/transactional` |

Los endpoints `/internal/**` no llevan JWT — estan protegidos a nivel de red (ClusterIP en Kubernetes, ver ADR-0005).

## Variables de entorno

| Variable | Descripcion | Default desarrollo |
|----------|-------------|-------------------|
| `PORT` | Puerto del servicio | `8081` |
| `JWT_SECRET` | Clave HS256 (minimo 256 bits) | — |
| `REDIS_HOST` / `REDIS_PORT` | Redis (refresh tokens + codigos de verificacion) | `localhost` / `6379` |
| `USER_SERVICE_URL` | URL interna de user-service | `http://localhost:8080` |
| `NOTIFICATION_SERVICE_URL` | URL interna de notification-service (envio de codigos) | `http://localhost:8087` |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth (validacion del `idToken`) | (vacio) |
| `EMAIL_VERIFICATION_TTL_MINUTES` | Vigencia del codigo | `10` |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS` | Cooldown entre reenvios | `60` |
| `EMAIL_VERIFICATION_MAX_ATTEMPTS` | Intentos maximos por codigo | `5` |

El `JWT_SECRET` debe ser **identico** al configurado en `api-gateway` (que valida los tokens emitidos por este servicio).

## Division de responsabilidades de auth

| Quien | Responsabilidad |
|-------|----------------|
| `auth-service` | Verifica credenciales (login) y **emite** JWTs |
| `api-gateway` | **Valida** JWTs en todas las requests posteriores (ADR-0004) |
| Servicios backend | Confian en los headers `X-User-Id`, `X-User-Role`, `X-User-Permissions` inyectados por el gateway |

`auth-service` no valida JWTs entrantes: el gateway ya lo hizo antes de rutear la request a este servicio.

## Tests

```bash
mvn test   # Unit tests (Mockito, sin Spring context completo)
```
