variable "project_id" {
  description = "Proyecto GCP destino"
  type        = string
}

variable "region" {
  description = "Region GCP"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona GCP"
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  description = "Nombre del cluster GKE"
  type        = string
  default     = "liken-gke"
}

variable "namespace" {
  description = "Namespace donde vive la app"
  type        = string
  default     = "app"
}

variable "artifact_registry_repository_id" {
  description = "Nombre del repositorio Docker en Artifact Registry"
  type        = string
  default     = "liken-platform"
}

variable "bucket_name" {
  description = "Bucket GCS para documentos KYC y proyectos"
  type        = string
  default     = "liken-documents"
}

variable "node_machine_type" {
  description = "Tipo de nodo GKE"
  type        = string
  default     = "e2-standard-4"
}

variable "storage_ksa_name" {
  description = "Kubernetes ServiceAccount para Workload Identity"
  type        = string
  default     = "liken-storage-access"
}

variable "storage_gsa_account_id" {
  description = "Google Service Account para acceso a GCS"
  type        = string
  default     = "liken-storage-access"
}
