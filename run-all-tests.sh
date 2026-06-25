#!/usr/bin/env bash
#
# run-all-tests.sh — corre los tests de los 8 microservicios y da un resumen
# consolidado. Pensado para chequear que esté todo verde después de cada merge.
#
# Uso:
#   ./run-all-tests.sh           # corre todos los servicios
#   ./run-all-tests.sh user-service marketplace-service   # solo algunos
#
# Salida: tabla pass/fail por servicio + conteo de tests. Exit code != 0 si
# algún servicio falla (sirve para CI).

set -u

SERVICES=(
  auth-service
  blockchain-service
  invest-dividend-service
  marketplace-service
  notification-service
  oracle-service
  project-service
  user-service
  wallet-service
)

# Si se pasan args, correr solo esos servicios.
if [ "$#" -gt 0 ]; then
  SERVICES=("$@")
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/.test-logs"
mkdir -p "$LOG_DIR"

declare -A RESULT
declare -A SUMMARY
FAILED=0

echo "Corriendo tests en ${#SERVICES[@]} servicio(s)..."
echo

for svc in "${SERVICES[@]}"; do
  printf "  %-26s " "$svc"
  log="$LOG_DIR/$svc.log"
  if mvn -f "$ROOT/services/$svc/pom.xml" test >"$log" 2>&1; then
    RESULT[$svc]="PASS"
  else
    RESULT[$svc]="FAIL"
    FAILED=$((FAILED + 1))
  fi
  # Extraer el último "Tests run: ..." agregado del reporte
  SUMMARY[$svc]=$(grep -hoE "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+" "$log" \
    | tail -1)
  echo "${RESULT[$svc]}"
done

echo
echo "──────────────────────────────────────────────────────────────"
printf "%-26s %-6s %s\n" "SERVICIO" "ESTADO" "TESTS"
echo "──────────────────────────────────────────────────────────────"
for svc in "${SERVICES[@]}"; do
  printf "%-26s %-6s %s\n" "$svc" "${RESULT[$svc]}" "${SUMMARY[$svc]:-(sin reporte)}"
done
echo "──────────────────────────────────────────────────────────────"

if [ "$FAILED" -eq 0 ]; then
  echo "✅ Todo verde. Logs en $LOG_DIR/"
  exit 0
else
  echo "❌ $FAILED servicio(s) con fallos. Revisar logs en $LOG_DIR/"
  exit 1
fi
