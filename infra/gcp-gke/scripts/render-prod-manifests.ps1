param(
  [Parameter(Mandatory = $true)]
  [string]$ValuesFile,

  [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$infraRoot = Split-Path -Parent $scriptRoot
$overlaySource = Join-Path $infraRoot "manifests\overlays\prod"
$baseSource = Join-Path $infraRoot "manifests\base"
$argoSource = Join-Path $infraRoot "manifests\argocd\liken-platform.application.yaml"

if (-not (Test-Path $ValuesFile)) {
  throw "No existe el archivo de valores: $ValuesFile"
}

. $ValuesFile

if (-not $Values) {
  throw "El archivo de valores debe definir una hashtable llamada `$Values."
}

$requiredKeys = @(
  "ProjectId",
  "RegistryUrl",
  "GcsBucketName",
  "FrontendOrigin",
  "StaticIpName",
  "WorkloadIdentityGsa",
  "ManifestsRepoUrl",
  "ManifestsBranch"
)

foreach ($key in $requiredKeys) {
  if (-not $Values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$Values[$key])) {
    throw "Falta completar `$$Values.$key en $ValuesFile"
  }
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $infraRoot "rendered"
}

$overlayTarget = Join-Path $OutputRoot "overlays\prod"
$baseTarget = Join-Path $OutputRoot "base"
$argoTargetDir = Join-Path $OutputRoot "argocd"
$argoTarget = Join-Path $argoTargetDir "liken-platform.application.yaml"

New-Item -ItemType Directory -Force -Path $overlayTarget | Out-Null
New-Item -ItemType Directory -Force -Path $baseTarget | Out-Null
New-Item -ItemType Directory -Force -Path $argoTargetDir | Out-Null

Copy-Item -Path (Join-Path $baseSource "*") -Destination $baseTarget -Recurse -Force

Get-ChildItem -File $overlaySource | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("__PROJECT_ID__", $Values.ProjectId)
  $content = $content.Replace("__GCS_BUCKET_NAME__", $Values.GcsBucketName)
  $content = $content.Replace("__FRONTEND_ORIGIN__", $Values.FrontendOrigin)
  $content = $content.Replace("__STATIC_IP_NAME__", $Values.StaticIpName)
  $content = $content.Replace("__WORKLOAD_IDENTITY_GSA__", $Values.WorkloadIdentityGsa)
  $content = $content.Replace("__REGISTRY_URL__", $Values.RegistryUrl)

  $target = Join-Path $overlayTarget $_.Name
  Set-Content -Path $target -Value $content -Encoding utf8
}

$argoContent = Get-Content $argoSource -Raw
$argoContent = $argoContent.Replace("__MANIFESTS_REPO_URL__", $Values.ManifestsRepoUrl)
$argoContent = $argoContent.Replace("__MANIFESTS_BRANCH__", $Values.ManifestsBranch)
Set-Content -Path $argoTarget -Value $argoContent -Encoding utf8

Write-Host "Render completado en: $OutputRoot"
Write-Host "Overlay prod: $overlayTarget"
Write-Host "ArgoCD app: $argoTarget"
