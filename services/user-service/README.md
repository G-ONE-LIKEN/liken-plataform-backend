# user-service

Microservicio de gestion de identidad y control de acceso de la plataforma LIKEN.

## Responsabilidades

- Registro y ciclo de vida de usuarios (alta, edicion, baja logica, activacion).
- Perfil del usuario autenticado (`/me`, `/me/profile`).
- **Vinculacion de wallet** Web3 mediante challenge nonce + firma (`/me/wallet/nonce` → firmar → `/me/wallet`). Al vincularse publica `user.wallet_linked`.
- Modelo de roles y permisos (RBAC).
- **Flujo de developer**: alta como developer y aprobacion/rechazo por ADMIN; emite `user.developer_registered` y `user.developer_status_changed`.
- **KYC**: subida de documentos a GCS, estados `NOT_STARTED / PENDING / APPROVED / REJECTED`, aprobacion por ADMIN (ADR-0015).
- **Tiers de inversor** (`BRONZE / SILVER / GOLD`): consume `user.tier_changed` de invest-dividend-service y actualiza `users.tier`.
- Endpoints internos que proveen datos de usuario a auth-service, gateway y servicios de negocio.

> El login y el cambio de contraseña son responsabilidad de **auth-service**, que llama a este servicio a través de los endpoints `/internal/**`.

### Eventos Kafka

| Topic | Rol |
|---|---|
| `user.registered` | **Publica** (lo consume notification-service). |
| `user.wallet_linked` | **Publica** (lo consumen invest-dividend-service y blockchain-service). |
| `user.developer_registered` | **Publica** (notifica a admins). |
| `user.developer_status_changed` | **Publica** (notifica al developer). |
| `user.context_invalidated` | **Publica** (invalida la caché de identidad del gateway). |
| `user.tier_changed` | **Consume** (actualiza `users.tier`). |

## Estructura del dominio

```
com.plataforma/
├── Application.java
│
├── user/                        # Gestion de usuarios
│   ├── controller/
│   │   ├── UserController.java          # /api/users/**
│   │   └── UserInternalController.java  # /internal/users/**
│   ├── dto/
│   ├── model/
│   │   ├── User.java                    # con campos tier y kycStatus
│   │   ├── Tier.java                    # enum BRONZE/SILVER/GOLD con umbrales
│   │   └── KycStatus.java               # enum NOT_STARTED/PENDING/APPROVED/REJECTED
│   ├── repository/
│   │   └── UserRepository.java
│   ├── service/
│   │   └── UserService.java
│   ├── kyc/                             # KYC (ADR-0015)
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
└── shared/                      # Transversal a todos los modulos
    ├── config/
    │   ├── SecurityConfig.java          # Rutas publicas vs protegidas
    │   ├── GcsConfig.java               # Cliente GCS para upload de docs KYC (ADR-0016)
    │   └── DataSeeder.java              # Seed inicial de roles/permisos en dev
    ├── dto/
    │   └── ApiResponse.java             # Wrapper estandar { success, data, message }
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   └── ...                          # UserNotFoundException, RoleNotFoundException, etc.
    ├── model/
    │   └── Auditable.java               # createdAt / updatedAt via @PrePersist/@PreUpdate
    └── security/
        └── GatewayHeaderAuthFilter.java # Lee X-User-Id/Role/Permissions del gateway (ADR-0004)
```

## Stack

| Capa | Tecnologia |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Seguridad | Spring Security + `GatewayHeaderAuthFilter` (ADR-0004) |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Tests unitarios | JUnit 5 + Mockito (H2 en memoria) |
| Tests de integracion | JUnit 5 + PostgreSQL real (docker-compose) |

## Endpoints publicos

### Usuarios y perfil propio
| Método | Ruta | Acceso |
|--------|------|--------|
| POST | `/api/users` | Publico (registro) |
| GET | `/api/users/me` | Autenticado |
| GET | `/api/users/me/profile` | Autenticado |
| PUT | `/api/users/me/profile` | Autenticado |
| POST | `/api/users/me/wallet/nonce` | Autenticado (genera el challenge a firmar) |
| POST | `/api/users/me/wallet` | Autenticado (verifica la firma y vincula la wallet) |
| POST | `/api/users/me/kyc` | Autenticado (multipart — sube documentos) |
| GET | `/api/users/me/kyc` | Autenticado (estado del KYC propio) |

### Administracion de usuarios
| Método | Ruta | Acceso |
|--------|------|--------|
| GET | `/api/users` | `ADMIN` |
| GET | `/api/users/{id}` | `ADMIN` |
| PUT | `/api/users/{id}` | `ADMIN` |
| PUT | `/api/users/{id}/role` | `ADMIN` |
| PUT | `/api/users/{id}/activate` | `ADMIN` |
| DELETE | `/api/users/{id}` | `ADMIN` (soft delete) |
| GET | `/api/users/developers` | `ADMIN` (lista de developers) |
| PUT | `/api/users/{id}/developer-status` | `ADMIN` (aprobar/rechazar developer) |
| PUT | `/api/users/{id}/kyc` | `ADMIN` (aprobar/rechazar KYC) |

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

Usados por auth-service. No estan expuestos fuera del cluster (ver ADR-0005).

| Método | Ruta | Usado por | Descripcion |
|--------|------|-----------|-------------|
| GET | `/internal/users/by-email/{email}` | auth-service | Buscar usuario para login |
| GET | `/internal/users/{id}` | auth-service | Datos de usuario por ID |
| GET | `/internal/users/{id}/context` | gateway | Rol + permisos para la caché de identidad |
| GET | `/internal/users/{id}/contact` | servicios | Email/nombre para notificaciones |
| GET | `/internal/users/by-wallet/{address}` | blockchain, invest | Resolver `walletAddress → userId` |
| GET | `/internal/users/by-audience` | notification | IDs por audiencia (ADMINS, DEVELOPERS, ...) |
| GET | `/internal/users/{id}/kyc-status` | servicios | Estado KYC del usuario |
| POST | `/internal/users/local` | auth-service | Alta tras verificacion de email |
| POST | `/internal/users/google` | auth-service | Provisionar usuario de Google |
| PUT | `/internal/users/{id}/google` | auth-service | Vincular cuenta Google |
| PUT | `/internal/users/{id}/password` | auth-service | Actualizar contraseña (ya hasheada) |
| PUT | `/internal/users/{id}/email-verified` | auth-service | Marcar email como verificado |

## Roles predefinidos

| Rol | Notas |
|-----|-------|
| `BASIC` | Usuario recién registrado, sin permisos especiales. |
| `INVESTOR` | Permisos de inversion (`investment:*`). |
| `DEVELOPER` | Permisos de proyectos (`project:*`). |
| `ADMIN` | Todos los permisos + gestion de roles. |
| `SUPER_ADMIN` | Admin con privilegios elevados (no degradable por otro admin). |

> **Tiers** (distinto de los roles): `BRONZE` (default) → `SILVER` (≥ $1000) → `GOLD` (≥ $5000). Los calcula invest-dividend-service y los sincroniza via `user.tier_changed`.

## Migraciones Flyway

| Version | Contenido |
|---------|-----------|
| V1 | Tablas: `roles`, `permissions`, `roles_permissions`, `users` |
| V2 | Seed de roles y permisos iniciales |
| V3 | `wallets` (ya migrada a wallet-service) |
| V4 | `token_price` en projects (ya migrada a invest-dividend-service) |
| V5 | Drop de tablas obsoletas: `investments`, `user_projects`, `wallets`, `projects` |

## Variables de entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `PORT` | `8080` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/user_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |

## Levantar en local

```bash
# Desde la raiz del repo
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

# Integracion (requiere Postgres corriendo)
docker compose up postgres -d
mvn test -Pintegration
```

### Convenciones de tests de integracion

- Extender `AbstractIntegrationTest`: provee `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("integration")` y `@Transactional` (rollback automatico por test).
- No crear roles en los tests — Flyway ya los sembro. Buscarlos con `roleRepository.findByName(RoleConstants.X)`.
- Usar emails unicos para evitar colisiones: `"user+" + System.nanoTime() + "@mail.com"`.
- La autenticacion en tests se simula inyectando headers `X-User-Id`, `X-User-Role`, `X-User-Permissions` directamente via `RequestPostProcessor`, tal como lo hace el gateway en produccion (ADR-0004).
