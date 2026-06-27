# LIKEN en GCP con GKE + ArgoCD

Esta carpeta deja una base de CI/CD para desplegar LIKEN en GCP con la topologia:

1. `liken-plataform-backend` y `liken-plataform-frontend` construyen imagenes con GitHub Actions.
2. Las imagenes se publican en Artifact Registry.
3. GitHub Actions aplica los manifests a GKE cuando hay push a `main` o `master`.

## Estructura

```text
infra/gcp-gke/
├── terraform/                    # GKE, Artifact Registry, bucket GCS, IP estatica y Workload Identity
├── manifests/
│   ├── base/                     # Deployments, Services, Ingress y dependencias base
│   ├── overlays/prod/            # Config de produccion para GKE
│   └── argocd/                   # Opcional si mas adelante queres GitOps con ArgoCD
```

## Repos involucrados

- Repo app backend: este repo.
- Repo app frontend: `C:\Users\Gonza\programacion\SIP\liken-plataform-frontend`

## Como funciona con 2 repos

La forma elegida es:

- `liken-plataform-backend` es el repo que mantiene la infraestructura Kubernetes.
- `liken-plataform-backend` hace build, push y aplica los manifests base a GKE.
- `liken-plataform-frontend` hace build, push y actualiza solo la imagen del deployment `frontend`.

En otras palabras:

- Backend repo = define la plataforma.
- Frontend repo = actualiza solo su contenedor dentro de esa plataforma.

## Bootstrap recomendado

### 1. Crear la infraestructura GCP

Copiar:

```powershell
Copy-Item .\infra\gcp-gke\terraform\terraform.tfvars.example .\infra\gcp-gke\terraform\terraform.tfvars
```

Completar `terraform.tfvars` y luego:

```powershell
cd .\infra\gcp-gke\terraform
terraform init
terraform plan
terraform apply
```

Outputs importantes:

- `registry_url`
- `gcs_bucket_name`
- `ingress_ip_address`
- `ingress_ip_name`
- `workload_identity_gsa_email`
- `kubeconfig_command`

### 2. Configurar GitHub Secrets para deploy automatico

En `liken-plataform-backend`:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`
- `GCP_PROJECT_ID` = `liken-plataform`
- `GCP_REGION` = `us-central1`
- `ARTIFACT_REGISTRY_REPOSITORY` = `liken-platform`
- `GKE_CLUSTER_NAME` = `liken-gke`
- `GKE_CLUSTER_ZONE` = `us-central1-a`
- `GCS_BUCKET_NAME` = `liken-documents-gonza-2026`
- `FRONTEND_ORIGIN` = `https://www.liken.lat` (el dominio canónico; `liken.lat` redirige 301 al www desde el frontend)
- `GKE_STATIC_IP_NAME` = `liken-gke-ingress-ip`
- `GKE_WORKLOAD_IDENTITY_GSA` = `liken-storage-access@liken-plataform.iam.gserviceaccount.com`

En `liken-plataform-frontend`:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`
- `GCP_PROJECT_ID` = `liken-plataform`
- `GCP_REGION` = `us-central1`
- `ARTIFACT_REGISTRY_REPOSITORY` = `liken-platform`
- `GKE_CLUSTER_NAME` = `liken-gke`
- `GKE_CLUSTER_ZONE` = `us-central1-a`

Y como `Repository variables` en el repo frontend:

- `NEXT_PUBLIC_API_URL`
- `NEXT_PUBLIC_WC_PROJECT_ID`
- `NEXT_PUBLIC_LKN_ADDRESS`
- `NEXT_PUBLIC_REGISTRY_ADDRESS`
- `NEXT_PUBLIC_DISTRIBUTOR_ADDRESS`
- `NEXT_PUBLIC_USDC_ADDRESS`
- `NEXT_PUBLIC_CHAIN_ID`
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID`

Valores sugeridos para este entorno:

- `NEXT_PUBLIC_API_URL` = `/` — **same-origin**: el front usa rutas relativas (`/api/...`) y el ingress enruta `/api` al gateway bajo el mismo dominio. Sin CORS y funciona igual entrando por `liken.lat` o `www.liken.lat`. (Nunca usar la IP: el certificado solo es válido para los dominios.)
- `NEXT_PUBLIC_WC_PROJECT_ID` = tu WalletConnect project id
- `NEXT_PUBLIC_LKN_ADDRESS` = direccion del token LKN
- `NEXT_PUBLIC_REGISTRY_ADDRESS` = direccion del registry/marketplace
- `NEXT_PUBLIC_DISTRIBUTOR_ADDRESS` = direccion del distributor
- `NEXT_PUBLIC_USDC_ADDRESS` = `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238`
- `NEXT_PUBLIC_CHAIN_ID` = `11155111`
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID` = tu client id de Google

### Workload Identity para GitHub Actions

Los dos secretos que no salen de Kubernetes ni del cluster son:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

Esos corresponden a la federacion de GitHub Actions contra GCP. Si todavia no los creaste, tenes que:

1. Crear un service account para CI/CD.
2. Darle permisos sobre Artifact Registry y GKE.
3. Crear el Workload Identity Provider para GitHub.
4. Cargar esos dos valores en ambos repos.

Los workflows ya usan esos secretos con `google-github-actions/auth@v2`.

### 3. Renderizar los manifests de produccion

Copiar el archivo de ejemplo:

```powershell
Copy-Item .\infra\gcp-gke\scripts\prod-values.example.ps1 .\infra\gcp-gke\scripts\prod-values.ps1
```

Completar los valores reales y renderizar:

```powershell
.\infra\gcp-gke\scripts\render-prod-manifests.ps1 -ValuesFile .\infra\gcp-gke\scripts\prod-values.ps1
```

Esto genera:

- `infra/gcp-gke/rendered/overlays/prod`
- `infra/gcp-gke/rendered/argocd/liken-platform.application.yaml`

### 4. Crear el secret inicial en Kubernetes

Duplicar `secrets.example.yaml` como `secrets.yaml`, cargar los secretos reales y aplicarlo una sola vez:

```powershell
kubectl apply -f .\secrets.yaml
```

> ⚠️ `secrets.yaml` y `rendered/` están **gitignorados** (ADR-0026): el secret
> vive solo en el cluster, nunca en el repo. El CI no lo necesita (la
> kustomization no lo referencia). Si un secreto se filtra, la mitigación es
> **rotarlo**, no borrarlo del historial.

### 5. Activar el CI/CD

Con eso listo:

- Push a `main` o `master` en backend: build, push y apply de la plataforma en GKE.
- Push a `main` o `master` en frontend: build, push y update de la imagen del deployment `frontend`.

## Decisiones de esta base

- GCS real en produccion, sin `fake-gcs`.
- `user-service` y `project-service` usan Workload Identity para acceder al bucket.
- Ingress GCE con IP global estatica + Managed Certificate para `liken.lat` / `www.liken.lat` + redirect HTTP→HTTPS (FrontendConfig).
- Postgres, Redis, Kafka y Zookeeper corren dentro del cluster **con persistencia** (ADR-0027): PVCs para los cuatro, `strategy: Recreate` (RWO), Redis con AOF, y CronJob diario de `pg_dumpall` a GCS (`backup-cronjob.yaml`; restore en [`docs/runbook-backups.md`](../../docs/runbook-backups.md)).
- Logging en GKE con perfil Spring `k8s` (configmap): solo consola JSON con campo `severity` — Cloud Logging la parsea y no hay appenders a archivo (ADR-0025).

## Siguientes mejoras recomendadas

- Mover Postgres a Cloud SQL y Redis a Memorystore (la señal: cuando operarlos in-cluster cueste mas que el ahorro).
- Migrar Kafka a KRaft (elimina Zookeeper) o a un servicio administrado.
- GCP Secret Manager + External Secrets Operator (el Secret nunca existe como archivo).
- Snapshot schedule del PD de Postgres y lifecycle rule del bucket de backups (pasos en el runbook).
- Incorporar ArgoCD Image Updater si queres evitar que Actions toque el repo de manifests.
