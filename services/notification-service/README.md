# notification-service

> **Estado: ⏳ pendiente de implementación** (ver Paso 7 en `docs/plan-mvp.md`)

Microservicio de la plataforma LIKEN responsable del envío de notificaciones por email a los usuarios en respuesta a eventos relevantes del sistema.

## Responsabilidades

- Consumir eventos de Kafka definidos por DD010 y transformarlos en emails
- Renderizar templates HTML por tipo de evento
- Envío de emails transaccionales (confirmación de inversión, dividendo, KYC aprobado, etc.)
- Idempotencia: descartar eventos duplicados vía `eventId` (DD010) — opcionalmente persiste audit log en tabla `notifications`
- Monitorear tópicos `<tópico>.dlq` para alertar a admins sobre eventos que fallaron 3 veces (DD010)
- Sin API pública: opera exclusivamente como consumidor de Kafka. Solo expone `/actuator/health`

## Stack previsto

| Capa | Tecnología |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Mensajería | Apache Kafka (solo consumer) |
| Email | Spring Mail (SMTP) — MailHog en dev, **proveedor SMTP en prod a decidir** |
| Templates | Thymeleaf (HTML inline + plain text fallback) |
| Persistencia | Opcional: PostgreSQL + Flyway para audit log de notificaciones enviadas |
| Tests | JUnit 5 + Mockito + spring-kafka-test |

> **Sobre el proveedor SMTP en prod:** DD014 estableció GCP como cloud. AWS SES queda
> descartado. Candidatos a evaluar cuando se implemente: SendGrid, Google Workspace SMTP
> (si el equipo ya tiene Workspace), Mailgun, Postmark. Decisión a documentar en un DD nuevo.

## Sin endpoints públicos

Este servicio no se rutea desde el `api-gateway`. Opera exclusivamente consumiendo Kafka.
El único endpoint expuesto es `/actuator/health` para que el docker-compose pueda chequear su salud.

## Eventos Kafka consumidos

Todos los eventos siguen el modelo canónico DD010 (incluyen `eventId`, `occurredAt`, `version`).

| Tópico | Publicado por | Notificación que genera |
|--------|--------------|-------------------------|
| `investment.token_purchased` | invest-dividend | "Compra de tokens confirmada" al inversor |
| `dividends.distributed` | invest-dividend | "Dividendo acreditado" al inversor |
| `marketplace.order_matched` | marketplace | "Tu orden fue ejecutada" al vendedor Y al comprador |
| `projects.state_changed` | project-service | "Tu proyecto cambió de estado" al owner del proyecto |
| `wallets.funded` | wallet-service | "Depósito recibido en tu billetera" |
| `wallets.debited` | wallet-service | "Retiro procesado" |
| `user.tier_changed` | invest-dividend | "Subiste al tier {SILVER\|GOLD}" 🎉 |
| `<tópico>.dlq` | Kafka (auto) | Alerta a admin: "Evento {tópico} falló 3 veces" |

## Idempotencia (DD010)

Cada consumer chequea si ya procesó el `eventId` antes de enviar el email. Implementación recomendada (a confirmar al implementar):

- **Opción A — Sin DB propia:** cache local (Caffeine) con TTL de 24h con los `eventId` ya procesados. Simple pero pierde estado al reiniciar.
- **Opción B — Con tabla `notifications`:** persistir cada email enviado con `external_event_id UNIQUE`. Sirve también como audit log. Más robusto.

DD010 + el patrón de wallet-service (V2 migration) sugieren la opción B.

## Templates

Estructura sugerida en `src/main/resources/templates/`:
```
templates/
├── token-purchased.html
├── dividend-received.html
├── order-matched-seller.html
├── order-matched-buyer.html
├── project-state-changed.html
├── wallet-funded.html
├── wallet-debited.html
├── tier-upgraded.html
└── kyc-result.html         # invocado por user-service vía evento futuro
```

Cada template recibe el payload del evento como contexto Thymeleaf.

## Variables de entorno previstas

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8087` | Puerto (solo actuator/health) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
| `MAIL_HOST` | `localhost` | SMTP host (MailHog en dev) |
| `MAIL_PORT` | `1025` | SMTP port (MailHog en dev) |
| `MAIL_USERNAME` | — | Credencial SMTP (vacío en MailHog) |
| `MAIL_PASSWORD` | — | Credencial SMTP (vacío en MailHog) |
| `MAIL_FROM` | `noreply@liken.local` | Remitente |
| `DB_URL` | `jdbc:postgresql://localhost:5432/notification_db` | Solo si se elige Opción B de idempotencia |

## Para dev local: MailHog

Cuando se implemente, agregar al `docker-compose.yml` raíz:

```yaml
mailhog:
  image: mailhog/mailhog
  container_name: liken_mailhog
  ports:
    - "1025:1025"   # SMTP
    - "8025:8025"   # UI web — abrir en http://localhost:8025
```

MailHog captura los emails enviados sin entregarlos realmente — útil para inspeccionarlos en dev sin ensuciar inbox de nadie.

## Fuera del MVP

- Push notifications (móvil) — queda para V1.1, ver `docs/plan-mvp.md` Paso 10+
- SMS — no contemplado
- Internacionalización (i18n) de templates — V1.1
