# user-service

Microservicio de gestión de identidad y control de acceso de la plataforma LIKEN.

## Responsabilidades

- Registro y ciclo de vida de usuarios (alta, edición, baja lógica)
- Modelo de roles y permisos (RBAC)
- **KYC**: subida de documentos a GCS, estados `NOT_STARTED / PENDING / APPROVED / REJECTED`, aprobación por ADMIN (DD013)
- **Tiers de inversor** (`BASIC / SILVER / GOLD`): consume eventos `user.tier_changed` de invest-dividend-service
- Endpoints internos que proveen datos de usuario a auth-service, gateway y servicios de negocio

> El login y el cambio de contraseña son responsabilidad de **auth-service**, que llama a este servicio a través de los endpoints `/internal/**`.

## Estructura del dominio

```
com.plataforma/
├── Application.java
│
├── user/                        # Gestión de usuarios
│   ├── controller/
│   │   ├── UserController.java          # /api/users/**
│   │   └── UserInternalController.java  # /internal/users/**
│   ├── dto/
│   ├── model/
│   │   ├── User.java                    # con campos tier y kycStatus
│   │   ├── Tier.java                    # enum BASIC/SILVER/GOLD con umbrales
│   │   └── KycStatus.java               # enum NOT_STARTED/PENDING/APPROVED/REJECTED
│   ├── repository/
│   │   └── UserRepository.java
│   ├── service/
│   │   └── UserService.java
│   ├── kyc/                             # KYC (DD013)
│   │   ├── controller/                  # KycController + KycInternalController
│   │   ├── service/KycService.java
│   │   ├── repository/KycDocumentRepository.java
│   │   ├── model/                       # KycDocument + KycDocumentStatus
│   │   └── dto/
│   └── event/                           # Consumers Kafka
│       ├── consumer/UserTierChangedConsumer.java
│       └── dto/UserTierChangedEvent.java
│
├── rbac/                        # Roles y permisos
│   ├── constant/
│   │   ├── RoleConstants.java           # "ADMIN", "DEVELOPER", etc.
│   │   └── PermissionConstants.java
│   ├── controller/
│   │   ├── RoleController.java          # /api/roles/**
│   │   └── PermissionController.java    # /api/permissions/**
│   ├── dto/
│   │   └── RoleRequest.java
│   ├── model/
│   │   ├── Role.java
│   │   └── Permission.java
│   ├── repository/
│   │   ├── RoleRepository.java
│   │   └── PermissionRepository.java
│   └── service/
│       ├── RoleService.java
│       ├── PermissionService.java
│       └── AccessControlService.java    # Reglas RBAC (ej. admin no puede degradar a otro admin)
│
└── shared/                      # Transversal a todos los módulos
    ├── config/
    │   ├── SecurityConfig.java          # Rutas públicas vs protegidas
    │   ├── GcsConfig.java               # Cliente GCS para upload de docs KYC (DD014)
    │   └── DataSeeder.java              # Seed inicial de roles/permisos en dev
    ├── dto/
    │   └── ApiResponse.java             # Wrapper estándar { success, data, message }
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   └── ...                          # UserNotFoundException, RoleNotFoundException, etc.
    ├── model/
    │   └── Auditable.java               # createdAt / updatedAt vía @PrePersist/@PreUpdate
    └── security/
        └── GatewayHeaderAuthFilter.java # Lee X-User-Id/Role/Permissions del gateway (DD002)
```

## Stack

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Seguridad | Spring Security + `GatewayHeaderAuthFilter` (DD002) |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Tests unitarios | JUnit 5 + Mockito (H2 en memoria) |
| Tests de integración | JUnit 5 + PostgreSQL real (docker-compose) |

## Endpoints públicos

### Usuarios
| Método | Ruta | Acceso |
|--------|------|--------|
| POST | `/api/users` | Público (registro) |
| GET | `/api/users` | `ADMIN` |
| GET | `/api/users/{id}` | `ADMIN` |
| PUT | `/api/users/{id}` | `ADMIN` |
| PUT | `/api/users/{id}/role` | `ADMIN` |
| DELETE | `/api/users/{id}` | `ADMIN` (soft delete) |

### Roles
| Método | Ruta | Acceso |
|--------|------|--------|
| GET | `/api/roles` | `ADMIN` |
| POST | `/api/roles` | `ADMIN` |
| PUT | `/api/roles/{id}` | `ADMIN` |
| DELETE | `/api/roles/{id}` | `ADMIN` |
| PUT | `/api/roles/{id}/permissions` | `ADMIN` |

### Permisos
| Método | Ruta | Acceso |
|--------|------|--------|
| GET | `/api/permissions` | `ADMIN` |
| POST | `/api/permissions` | `ADMIN` |

## Endpoints internos (sin JWT, protegidos por red)

Usados por auth-service. No están expuestos fuera del clúster (ver DD003).

| Método | Ruta | Usado por | Descripción |
|--------|------|-----------|-------------|
| GET | `/internal/users/by-email/{email}` | auth-service | Buscar usuario para login |
| GET | `/internal/users/{id}` | auth-service | Datos de usuario por ID |
| PUT | `/internal/users/{id}/password` | auth-service | Actualizar contraseña (ya hasheada) |

## Roles predefinidos

| Rol | Permisos |
|-----|----------|
| `BASIC` | — |
| `INVESTOR` | `investment:*` |
| `DEVELOPER` | `project:*` |
| `ADMIN` | Todos los permisos + gestión de roles |

## Migraciones Flyway

| Versión | Contenido |
|---------|-----------|
| V1 | Tablas: `roles`, `permissions`, `roles_permissions`, `users` |
| V2 | Seed de roles y permisos iniciales |
| V3 | `wallets` (ya migrada a wallet-service) |
| V4 | `token_price` en projects (ya migrada a invest-dividend-service) |
| V5 | Drop de tablas obsoletas: `investments`, `user_projects`, `wallets`, `projects` |

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8080` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/user_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |

## Levantar en local

```bash
# Desde la raíz del repo
docker compose up --build

# Solo infraestructura + este servicio
docker compose up postgres -d
cd services/user-service
mvn spring-boot:run
```

## Tests

```bash
# Unitarios (H2, sin Docker)
mvn test

# Integración (requiere Postgres corriendo)
docker compose up postgres -d
mvn test -Pintegration
```

### Convenciones de tests de integración

- Extender `AbstractIntegrationTest`: provee `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("integration")` y `@Transactional` (rollback automático por test).
- No crear roles en los tests — Flyway ya los sembró. Buscarlos con `roleRepository.findByName(RoleConstants.X)`.
- Usar emails únicos para evitar colisiones: `"user+" + System.nanoTime() + "@mail.com"`.
- La autenticación en tests se simula inyectando headers `X-User-Id`, `X-User-Role`, `X-User-Permissions` directamente via `RequestPostProcessor`, tal como lo hace el gateway en producción (DD002).
