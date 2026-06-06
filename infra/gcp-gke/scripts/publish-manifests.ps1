param(
  [Parameter(Mandatory = $true)]
  [string]$RenderedRoot,

  [Parameter(Mandatory = $true)]
  [string]$DestinationRepoPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $RenderedRoot)) {
  throw "No existe el directorio renderizado: $RenderedRoot"
}

if (-not (Test-Path $DestinationRepoPath)) {
  throw "No existe el repo destino: $DestinationRepoPath"
}

$overlaySource = Join-Path $RenderedRoot "overlays"
$argoSource = Join-Path $RenderedRoot "argocd"

if (-not (Test-Path $overlaySource)) {
  throw "Falta la carpeta overlays renderizada en $RenderedRoot"
}

New-Item -ItemType Directory -Force -Path (Join-Path $DestinationRepoPath "overlays") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $DestinationRepoPath "argocd") | Out-Null

Copy-Item -Path (Join-Path $overlaySource "*") -Destination (Join-Path $DestinationRepoPath "overlays") -Recurse -Force

if (Test-Path $argoSource) {
  Copy-Item -Path (Join-Path $argoSource "*") -Destination (Join-Path $DestinationRepoPath "argocd") -Recurse -Force
}

Write-Host "Manifests copiados a: $DestinationRepoPath"
