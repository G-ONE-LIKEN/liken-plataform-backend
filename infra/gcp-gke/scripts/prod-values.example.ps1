$Values = @{
  ProjectId            = "tu-proyecto-gcp"
  Region               = "us-central1"
  RegistryUrl          = "us-central1-docker.pkg.dev/tu-proyecto-gcp/liken-platform"
  GcsBucketName        = "liken-documents-tu-sufijo"
  FrontendOrigin       = "https://liken.tu-dominio.com"
  StaticIpName         = "liken-gke-ingress-ip"
  WorkloadIdentityGsa  = "liken-storage-access@tu-proyecto-gcp.iam.gserviceaccount.com"
  ManifestsRepoUrl     = "https://github.com/tu-org/liken-platform-manifests.git"
  ManifestsBranch      = "main"
}
