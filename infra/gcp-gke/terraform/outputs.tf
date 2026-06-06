output "cluster_name" {
  value = google_container_cluster.primary.name
}

output "cluster_location" {
  value = google_container_cluster.primary.location
}

output "registry_url" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.liken.repository_id}"
}

output "gcs_bucket_name" {
  value = google_storage_bucket.documents.name
}

output "workload_identity_gsa_email" {
  value = google_service_account.workload_identity.email
}

output "ingress_ip_name" {
  value = google_compute_global_address.ingress_ip.name
}

output "ingress_ip_address" {
  value = google_compute_global_address.ingress_ip.address
}

output "kubeconfig_command" {
  value = "gcloud container clusters get-credentials ${var.cluster_name} --zone ${var.zone} --project ${var.project_id}"
}
