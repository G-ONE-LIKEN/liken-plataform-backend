# Runbook — Backups y recuperación de datos

> Complemento del ADR-0027. Todo asume `kubectl` apuntando a `liken-gke`,
> namespace `app`.

## Qué se respalda y cómo

| Dato | Mecanismo | Frecuencia | Retención |
|---|---|---|---|
| Postgres (todas las DBs) | CronJob `postgres-backup` → `pg_dumpall` comprimido a `gs://<bucket>/backups/` | Diario 03:00 AR | La del bucket (configurar lifecycle: 30 días recomendado) |
| Disco de Postgres | Snapshot schedule del Persistent Disk (manual, ver abajo) | Diario | 14 días |
| Kafka / Zookeeper | PVC (sobreviven re-schedules; sin backup externo: los eventos son reconstruibles re-indexando la chain) | — | — |
| Redis | AOF en PVC (sobrevive reinicios; pérdida tolerable: re-login) | — | — |

## Configurar el snapshot schedule del disco (una vez, manual)

```bash
# 1. Crear la política
gcloud compute resource-policies create snapshot-schedule liken-daily-snapshots \
  --project=<PROJECT_ID> --region=us-central1 \
  --max-retention-days=14 --start-time=04:00 --daily-schedule

# 2. Identificar el PD del PVC de postgres
kubectl -n app get pvc postgres-data -o jsonpath='{.spec.volumeName}'
gcloud compute disks list --filter="name~<volumeName>"

# 3. Asociar la política al disco
gcloud compute disks add-resource-policies <DISK_NAME> \
  --resource-policies=liken-daily-snapshots --zone=us-central1-a
```

## Probar el backup a demanda

```bash
kubectl -n app create job --from=cronjob/postgres-backup backup-manual-$(date +%s)
kubectl -n app logs -f job/backup-manual-<ts> -c dump
kubectl -n app logs -f job/backup-manual-<ts> -c upload
gsutil ls -lh gs://<GCS_BUCKET_NAME>/backups/ | tail
```

## Restore de Postgres desde un dump

⚠️ Práctica destructiva — hacerla primero contra una DB de prueba.

```bash
# 1. Bajar y descomprimir el dump
gsutil cp gs://<bucket>/backups/pg-<fecha>.sql.gz .
gunzip pg-<fecha>.sql.gz

# 2. (Restore total) Escalar los servicios a 0 para frenar escrituras
kubectl -n app scale deploy/user-service deploy/auth-service deploy/project-service \
  deploy/wallet-service deploy/invest-dividend-service deploy/notification-service \
  deploy/blockchain-service --replicas=0

# 3. Aplicar el dump (pg_dumpall incluye CREATE DATABASE/ROLE)
kubectl -n app exec -i deploy/postgres -- psql -U <POSTGRES_USER> -d postgres < pg-<fecha>.sql

# 4. Restaurar réplicas
kubectl -n app scale deploy/user-service ... --replicas=1
```

## Restore desde snapshot del disco (desastre total del PD)

```bash
gcloud compute disks create postgres-restored --source-snapshot=<SNAPSHOT> --zone=us-central1-a
# Crear PV/PVC apuntando al disco restaurado y repuntar el Deployment.
# (Más simple a esta escala: restaurar desde el dump de GCS, ver arriba.)
```

## Reconstruir proyecciones desde la chain (Kafka perdido)

Si a pesar del PVC se perdieran eventos: las proyecciones se reconstruyen
re-indexando (ADR-0017). Resetear el checkpoint del contrato afectado:

```sql
-- en blockchain_db
UPDATE indexer_checkpoint SET last_processed_block = <bloque_inicial>
WHERE contract_address = '<address>';
```

El indexer re-escanea y re-publica; la idempotencia por eventId en los
consumers absorbe los duplicados.

## Verificación periódica

El backup que no se probó no existe: una vez por cuatrimestre, restaurar el
último dump en una DB temporal y validar que `users`, `investments` y
`wallet_movement` tienen los conteos esperados.
