$Values = @{
  ProjectId            = "liken-plataform"
  Region               = "us-central1"
  RegistryUrl          = "us-central1-docker.pkg.dev/liken-plataform/liken-platform"
  GcsBucketName        = "liken-documents-gonza-2026"
  FrontendOrigin       = "http://34.160.119.148"
  StaticIpName         = "liken-gke-ingress-ip"
  WorkloadIdentityGsa  = "liken-storage-access@liken-plataform.iam.gserviceaccount.com"
  ManifestsRepoUrl     = "direct-apply"
  ManifestsBranch      = "main"
}