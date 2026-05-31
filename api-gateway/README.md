# api-gateway

Punto de entrada único de la plataforma LIKEN. Valida JWT, enruta requests a los servicios internos e inyecta la identidad del usuario como headers HTTP.

## Arquitectura

```
Frontend (5173)
      │
      ▼
api-gateway (8090)        ← validación JWT + CORS + enrutamiento
      │
      ├── /api/auth/**          ──▶  auth-service    (8081)
      ├── /api/users/**         ──▶  user-service    (8080)
      ├── /api/roles/**         ──▶  user-service    (8080)
      ├── /api/permissions/**   ──▶  user-service    (8080)
      └── /api/projects/**      ──▶  project-service (8082)
```

## Responsabilidades

- **Autenticación centralizada**: valida el JWT antes de que el request llegue a cualquier servicio. Token ausente o inválido → `401` sin tocar los backends (ver DD002).
- **Propagación de identidad**: inyecta tres headers en cada request autenticado:
  - `X-User-Id` — ID numérico del usuario
  - `X-User-Role` — nombre del rol (`ADMIN`, `DEVELOPER`, etc.)
  - `X-User-Permissions` — permisos separados por coma (`project:read,project:create`)
- **Rate limiting**: limita requests por IP (rutas públicas) o por usuario (rutas autenticadas) usando Redis. Devuelve `429` al exceder el límite (ver DD009).
- **Enrutamiento**: redirige cada request al microservicio correcto según el path.
- **CORS**: gestiona los headers CORS centralmente para el frontend.

## Rutas

| Path | Servicio destino | JWT requerido |
|------|-----------------|---------------|
| `POST /api/auth/login` | auth-service | No |
| `POST /api/auth/change-password` | auth-service | Sí |
| `POST /api/users` | user-service | No (registro público) |
| `/api/users/**` | user-service | Sí |
| `/api/roles/**` | user-service | Sí |
| `/api/permissions/**` | user-service | Sí |
| `/api/projects/**` | project-service | Sí |

## Rate limiting

Implementado con `RequestRateLimiter` de Spring Cloud Gateway + Redis (token bucket).

| Ruta | Algoritmo | Clave | Límite sostenido | Burst |
|------|-----------|-------|-----------------|-------|
| `/api/auth/**` | IP | IP del cliente | ~24 req/min | 2 req inmediatos |
| `/api/projects/**` | userId/IP | `X-User-Id` o IP | 30 req/s | 60 |
| `/api/users/**` | userId/IP | `X-User-Id` o IP | 20 req/s | 40 |
| `/api/roles/**`, `/api/permissions/**` | userId/IP | `X-User-Id` o IP | 10 req/s | 20 |

Al exceder el límite el gateway devuelve `429 Too Many Requests`. Si Redis no está disponible, los requests pasan (fail open) para no comprometer la disponibilidad.

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Gateway | Spring Cloud Gateway 2023.0.1 (reactivo / WebFlux) |
| JWT | JJWT 0.11.5 (HS256) |
| Rate limiting | Redis 7 (token bucket via `RequestRateLimiter`) |
| Tests | JUnit 5 + WireMock |

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8090` | Puerto del gateway |
| `JWT_SECRET` | `${JWT_SECRET}` | Clave HS256 compartida con todos los servicios |
| `FRONTEND_URL` | `http://localhost:5173` | Origen permitido en CORS |
| `AUTH_URL` | `http://localhost:8081` | URL de auth-service |
| `USUARIOS_URL` | `http://localhost:8080` | URL de user-service |
| `PROYECTOS_URL` | `http://localhost:8082` | URL de project-service |
| `REDIS_HOST` | `localhost` | Host de Redis (rate limiting) |
| `REDIS_PORT` | `6379` | Puerto de Redis |

## Levantar en local

```bash
# Desde la raíz del repo — levanta todo
docker compose up --build

# Solo el gateway (requiere que los demás servicios estén corriendo)
cd api-gateway
mvn spring-boot:run
```

## Estructura

```
src/main/java/com/plataforma/gateway/
├── Application.java
├── filter/
│   └── JwtAuthFilter.java     # GlobalFilter: valida JWT e inyecta headers
└── security/
    └── JwtUtils.java          # Parseo y validación de JWT
```

## Tests

```bash
mvn test
```

| Clase | Qué cubre |
|-------|-----------|
| `JwtUtilsTest` | Validación de firma, expiración y extracción de claims |
| `JwtAuthFilterTest` | Rutas públicas, token ausente/inválido/válido, headers inyectados |
| `GatewayRoutingTest` | Enrutamiento de cada path con WireMock, verificación de los tres headers |
