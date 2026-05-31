# invest-dividend-service

> **Estado: pendiente de implementación**

Microservicio de la plataforma LIKEN responsable de la gestión de inversiones y distribución de dividendos en proyectos de energía renovable.

## Responsabilidades

- Procesamiento de compras de tokens de proyectos
- Registro de posiciones de inversión por usuario
- Cálculo y distribución de dividendos cuando un proyecto genera rendimientos
- Publicación de eventos hacia project-service y wallet-service

## Stack previsto

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka |

## Endpoints previstos

| Método | Ruta | Permiso | Descripción |
|--------|------|---------|-------------|
| POST | `/api/investments` | `investment:create` | Comprar tokens de un proyecto |
| GET | `/api/investments` | `investment:read` | Listar inversiones del usuario autenticado |
| GET | `/api/investments/{id}` | `investment:read` | Detalle de una inversión |
| GET | `/api/dividends` | `investment:read` | Historial de dividendos recibidos |
| POST | `/api/dividends/distribute` | `ADMIN` | Disparar distribución de dividendos para un proyecto |

## Eventos Kafka

**Publica:**

| Tópico | Cuándo |
|--------|--------|
| `investment.token_purchased` | Al confirmar una compra de tokens |
| `dividends.distributed` | Al completar una distribución de dividendos |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `projects.state_changed` | project-service | Bloquear nuevas compras si el proyecto se cancela o cierra |

## Variables de entorno previstas

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8083` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/invest_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |
| `JWT_SECRET` | `${JWT_SECRET}` | Clave HS256 compartida |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
