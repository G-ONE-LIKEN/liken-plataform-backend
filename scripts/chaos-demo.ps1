# =============================================================================
# Demo de caos — Sprint 1 (resiliencia)
#
# Demuestra que la plataforma degrada de forma controlada cuando user-service
# cae, en vez de colapsar en cascada:
#   - Las rutas públicas siguen respondiendo.
#   - Las rutas autenticadas responden 503 inmediato (no timeout de 30s).
#   - El circuit breaker abre tras varias fallas y se recupera solo.
#
# Uso (con kubectl apuntando al clúster):
#   .\scripts\chaos-demo.ps1 -BaseUrl https://www.liken.lat [-Token <jwt>]
# =============================================================================
param(
  [string]$BaseUrl = "https://www.liken.lat",
  [string]$Token = "",
  [string]$Namespace = "app"
)

function Invoke-Timed($Name, $Url, $Headers) {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  try {
    $r = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 30
    $status = $r.StatusCode
  } catch {
    $resp = $_.Exception.Response
    if ($resp) { $status = [int]$resp.StatusCode } else { $status = "ERROR: $($_.Exception.Message)" }
  }
  $sw.Stop()
  "{0,-45} -> {1,5} en {2,6} ms" -f $Name, $status, $sw.ElapsedMilliseconds
}

$authHeaders = @{}
if ($Token) { $authHeaders["Authorization"] = "Bearer $Token" }

Write-Host "`n=== 1. Estado inicial (todo sano) ===" -ForegroundColor Cyan
Invoke-Timed "GET /api/projects (publica)" "$BaseUrl/api/projects" @{}
if ($Token) { Invoke-Timed "GET /api/users/me (autenticada)" "$BaseUrl/api/users/me" $authHeaders }

Write-Host "`n=== 2. Apagando user-service ===" -ForegroundColor Yellow
kubectl -n $Namespace scale deployment/user-service --replicas=0
Write-Host "Esperando a que el pod termine..."
kubectl -n $Namespace wait --for=delete pod -l app=user-service --timeout=60s 2>$null
Start-Sleep -Seconds 35   # deja expirar el cache de contexto del gateway (TTL 30s)

Write-Host "`n=== 3. Plataforma degradada (lo que hay que mostrar) ===" -ForegroundColor Cyan
Write-Host "Las publicas viven; las autenticadas fallan RAPIDO con 503 (no 401, no 30s):"
Invoke-Timed "GET /api/projects (publica)" "$BaseUrl/api/projects" @{}
if ($Token) {
  # Varias llamadas seguidas: las primeras pagan el timeout de 3s,
  # cuando el breaker abre las siguientes responden en milisegundos.
  1..6 | ForEach-Object {
    Invoke-Timed "GET /api/users/me (intento $_)" "$BaseUrl/api/users/me" $authHeaders
  }
}

Write-Host "`n=== 4. Restaurando user-service ===" -ForegroundColor Yellow
kubectl -n $Namespace scale deployment/user-service --replicas=1
kubectl -n $Namespace rollout status deployment/user-service --timeout=120s
Start-Sleep -Seconds 16   # el breaker pasa a half-open tras 15s

Write-Host "`n=== 5. Recuperacion automatica ===" -ForegroundColor Cyan
Invoke-Timed "GET /api/projects (publica)" "$BaseUrl/api/projects" @{}
if ($Token) { Invoke-Timed "GET /api/users/me (autenticada)" "$BaseUrl/api/users/me" $authHeaders }

Write-Host "`nEstado de los circuit breakers de auth-service:" -ForegroundColor Cyan
Write-Host "kubectl -n $Namespace port-forward deploy/auth-service 8081:8081"
Write-Host "curl http://localhost:8081/actuator/circuitbreakers`n"
