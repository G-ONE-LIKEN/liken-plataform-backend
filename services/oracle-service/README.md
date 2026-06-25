# oracle-service

Microservicio de la plataforma LIKEN que simula un oraculo IoT de generacion de energia. Consulta los proyectos activos en `project-service`, calcula cuanta energia generaria cada parque segun su capacidad instalada y la hora del dia, y publica el resultado para que otros servicios (ej. `invest-dividend-service`) lo usen como base para el calculo de dividendos.

## Responsabilidades

- **Simulacion de generacion solar**: calcula energia generada (kWh) en base a una curva senoidal acotada a horas de luz (6:00–18:00), con pico al mediodia y cero fuera de ese rango, mas una variacion aleatoria de ±10% para imitar el ruido de un medidor real.
- **Consulta periodica de proyectos activos**: cada ciclo, pregunta a `project-service` (via `/internal/projects/active`, llamada directa service-to-service, sin pasar por el gateway) que proyectos estan en estado `OPEN` y cual es su capacidad instalada.
- **Persistencia de lecturas**: guarda cada lectura simulada en `energy_readings`, con una restriccion unica por proyecto + timestamp para evitar duplicados.
- **Publicacion de eventos**: emite el evento `oracle.energy_reading` a Kafka por cada lectura generada.

## Stack

| Capa | Tecnologia |
|------|------------|
| Framework | Spring Boot 3.2.4 / Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| Mensajeria | Apache Kafka |
| Cliente HTTP | RestTemplate (consistente con el resto de los microservicios) |

## Dominio

```
com.plataforma.oracle/
├── client/       # ProjectServiceClient (consulta a project-service)
├── dto/          # ActiveProjectOracleDto
├── event/        # OracleEventPublisher
├── model/        # EnergyReading
├── repository/   # EnergyReadingRepository
├── scheduler/    # OracleScheduler (orquesta el ciclo completo)
└── simulator/    # SolarCurveSimulator
```

## Como funciona el ciclo

Cada `oracle.simulation.interval-ms` (default: 1 minuto), `OracleScheduler`:

1. Consulta `GET /internal/projects/active` en `project-service` para obtener los proyectos en estado `OPEN` con capacidad instalada.
2. Para cada proyecto, calcula la energia generada en el intervalo usando `SolarCurveSimulator`.
3. Si ya existe una lectura para ese proyecto en ese timestamp exacto, la omite (idempotencia a nivel de ciclo).
4. Guarda la lectura en `energy_readings` y publica el evento `oracle.energy_reading`.

Un error al procesar un proyecto puntual (ej. `project-service` no responde) no interrumpe el resto del ciclo — se loguea y se continua con los demas proyectos.

## Modelo de simulacion

```
factorSolar(hora) = sin(π × (hora - 6) / 12)   para hora ∈ [6, 18]
factorSolar(hora) = 0                           fuera de ese rango

potenciaMW   = installedCapacityMW × factorSolar(hora) × (1 ± 10% aleatorio)
energiaKWh   = potenciaMW × 1000 × (intervaloMinutos / 60)
```

No contempla estacionalidad, ubicacion geografica real, ni nubosidad — es una aproximacion deliberada para fines de demo/testing del flujo end-to-end, no un modelo fisico de generacion real.

## Endpoints

Este servicio no expone endpoints HTTP propios de negocio — toda su actividad es interna (consulta periodica + publicacion de eventos). Expone unicamente:

| Metodo | Path | Descripcion |
|--------|------|-------------|
| GET | `/actuator/health` | Healthcheck (usado por Docker/K8s) |

## Eventos Kafka

**Publica:**

| Topico | Payload | Cuando |
|--------|---------|--------|
| `oracle.energy_reading` | `projectId`, `readingTimestamp`, `energyKWh`, `timestamp` | Por cada lectura generada con energia simulada |

**Consume:** ninguno por ahora.

## Variables de entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `PORT` | `8088` | Puerto del servidor |
| `DB_URL` | `jdbc:postgresql://localhost:5432/oracle_db` | URL de PostgreSQL |
| `DB_USERNAME` | `liken_user` | Usuario de la DB |
| `DB_PASSWORD` | `${DB_PASSWORD}` | Contraseña de la DB |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
| `PROJECT_SERVICE_URL` | `http://localhost:8082` | URL de project-service (consulta directa, sin gateway) |
| `ORACLE_INTERVAL_MS` | `60000` | Intervalo entre ciclos de simulacion, en milisegundos (1 min por defecto) |

## Levantar en local

```bash
# Desde la raiz del repo — levanta toda la plataforma
docker compose up --build

# Solo infraestructura + este servicio
docker compose up postgres kafka -d
cd services/oracle-service
mvn spring-boot:run
```

## Verificar que esta funcionando

El oracle solo genera lecturas para proyectos en estado `OPEN` con `installedCapacityMW` configurado. Para confirmar que esta corriendo:

```bash
# Ver el ciclo en los logs
docker compose logs -f oracle-service

# Confirmar lecturas guardadas
docker exec -it liken_postgres psql -U liken_user -d oracle_db \
  -c "SELECT * FROM energy_readings ORDER BY reading_timestamp DESC LIMIT 10;"
```

Si no aparecen lecturas, verificar que exista al menos un proyecto en `project_db` con `state = 'OPEN'` y `installed_capacity_mw` no nulo.

## Pendiente

- **Consumidor del evento**: hoy ningun servicio escucha `oracle.energy_reading`. El consumo real (calculo de dividendos en base a la energia generada) queda a cargo de `invest-dividend-service`.
- Sin tests automatizados por el momento.