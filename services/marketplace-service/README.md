# marketplace-service

> **Estado: pendiente de implementación.** Hoy este directorio contiene solo este
> README de diseño: no hay código, no se buildea en CI y no tiene Deployment en
> GKE. Es el único servicio del MVP que sigue sin implementar. Todo lo que sigue
> es una propuesta tentativa, sujeta a cambios al implementarlo.

Microservicio de la plataforma LIKEN responsable del mercado secundario (P2P) de tokens de proyectos de energía renovable.

## Responsabilidades

- Publicación de órdenes de venta de tokens por parte de inversores
- Matching de órdenes de compra y venta
- Publicación de eventos al confirmar una transacción P2P
- Historial de transacciones del marketplace

## Stack previsto

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajería | Apache Kafka |

## Endpoints previstos

| Método | Ruta | Permiso | Descripción |
|--------|------|---------|-------------|
| GET | `/api/marketplace/orders` | Público | Listar órdenes activas de venta |
| POST | `/api/marketplace/orders` | `investment:read` | Publicar orden de venta de tokens |
| DELETE | `/api/marketplace/orders/{id}` | Owner | Cancelar una orden propia |
| POST | `/api/marketplace/orders/{id}/buy` | `investment:create` | Comprar tokens de una orden |
| GET | `/api/marketplace/transactions` | Autenticado | Historial de transacciones propias |

## Eventos Kafka

**Publica:**

| Tópico | Cuándo |
|--------|--------|
| `marketplace.order_matched` | Al concretar una transacción P2P |

**Consume:**

| Tópico | Publicado por | Para qué |
|--------|--------------|---------|
| `projects.state_changed` | project-service | Cancelar órdenes activas si el proyecto se cierra o cancela |

## Variables de entorno previstas

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8086` | Puerto del servidor (8085 ya lo usa blockchain-service) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/marketplace_db` | URL de PostgreSQL |
| `DB_USERNAME` | `dev_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |
| `JWT_SECRET` | `dev-secret-...` | Clave HS256 compartida |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
