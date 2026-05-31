# auth-service

Microservicio de autenticación de la plataforma LIKEN. Emite y gestiona tokens JWT. Es el único servicio que conoce las credenciales de los usuarios.

## Responsabilidades

- Login: verifica credenciales y emite JWT
- Cambio de contraseña: verifica la contraseña actual y actualiza la nueva
- Comunicación interna con `user-service` para leer/escribir datos de usuario

## Por qué es un servicio separado

Ver DD006 en `docs/decisiones-de-diseno.md`. Resumen: auth tiene un ciclo de vida y responsabilidades distintos al CRUD de usuarios. Separarlo permite escalar, auditar y evolucionar la estrategia de autenticación de forma independiente.

## Dominio

```
com.plataforma
├── auth/
│   ├── controller/AuthController.java
│   ├── service/AuthService.java
│   └── dto/LoginRequest.java, ChangePasswordRequest.java
└── shared/
    ├── client/UserServiceClient.java       # HTTP interno a user-service
    ├── client/dto/UserAuthDTO.java
    ├── config/AppConfig.java               # RestTemplate, PasswordEncoder
    ├── config/SecurityConfig.java          # Stateless, todo público (sin filtro JWT)
    ├── dto/ApiResponse.java
    ├── exception/...
    └── security/JwtUtils.java              # generateToken (login emite JWT)
```

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| JWT | JJWT 0.11.5 (HS256) |
| HTTP cliente | RestTemplate (Spring Web) |
| Tests | JUnit 5 + Mockito |

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/auth/login` | Verifica credenciales y devuelve JWT |
| POST | `/api/auth/change-password` | Cambia contraseña del usuario autenticado |

### Login
```json
POST /api/auth/login
{ "email": "user@mail.com", "password": "secreto" }

→ 200 { "data": "<jwt-token>", ... }
→ 401 si credenciales inválidas o cuenta inactiva
```

### Cambio de contraseña
```json
POST /api/auth/change-password
X-User-Id: 42                    # inyectado por el gateway (DD002)
{ "oldPassword": "actual", "newPassword": "nueva" }

→ 200 si exitoso
→ 401 si contraseña actual incorrecta
```

> El gateway valida el JWT antes de rutear y inyecta `X-User-Id`. auth-service confía en ese header y no parsea el token.

## Comunicación interna con user-service

| Llamada | Endpoint en user-service |
|---------|--------------------------|
| Buscar usuario por email (login) | `GET /internal/users/by-email/{email}` |
| Buscar usuario por ID (changePassword) | `GET /internal/users/{id}` |
| Actualizar contraseña | `PUT /internal/users/{id}/password` |

Los endpoints `/internal/**` de user-service no llevan JWT — están protegidos a nivel de red (ClusterIP en Kubernetes, ver DD003).

## Variables de entorno

| Variable | Descripción | Default desarrollo |
|----------|-------------|-------------------|
| `JWT_SECRET` | Clave HS256 (mínimo 256 bits) | valor en application.properties |
| `USER_SERVICE_URL` | URL interna de user-service | `http://localhost:8080` |
| `PORT` | Puerto del servicio | `8081` |

El `JWT_SECRET` debe ser **idéntico** al configurado en `api-gateway` (que valida los tokens emitidos por este servicio).

## División de responsabilidades de auth

| Quién | Responsabilidad |
|-------|----------------|
| `auth-service` | Verifica credenciales (login) y **emite** JWTs |
| `api-gateway` | **Valida** JWTs en todas las requests posteriores (DD002) |
| Servicios backend | Confían en los headers `X-User-Id`, `X-User-Role`, `X-User-Permissions` inyectados por el gateway |

`auth-service` no valida JWTs entrantes: el gateway ya lo hizo antes de rutear la request a este servicio.

## Tests

```bash
mvn test   # Unit tests (Mockito, sin Spring context completo)
```
