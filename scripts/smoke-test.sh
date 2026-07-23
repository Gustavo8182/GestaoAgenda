#!/usr/bin/env bash
set -euo pipefail

# Smoke test mínimo para qualquer ambiente já no ar (local, homologação ou produção).
# Não builda nada, não depende de código-fonte — só confirma que a API está de pé,
# respondendo e com a segurança básica esperada. Uso: ./scripts/smoke-test.sh [base-url]

BASE_URL="${1:-http://localhost:8080}"
FAILURES=0

check() {
  local description="$1"
  local url="$2"
  local expected_status="$3"

  local status
  status="$(curl -s -o /tmp/smoke-test-body.$$ -w "%{http_code}" "$url" || true)"
  status="${status: -3}"

  if [[ "$status" == "$expected_status" ]]; then
    echo "OK   $description ($status)"
  else
    echo "FAIL $description — esperado $expected_status, recebido $status"
    FAILURES=$((FAILURES + 1))
  fi
  rm -f /tmp/smoke-test-body.$$
}

echo "Smoke test contra: $BASE_URL"
echo

check "Health check"                 "$BASE_URL/actuator/health"       200
check "Status da API"                "$BASE_URL/api/v1/system/status"  200
check "Endpoint protegido nega sem sessão" "$BASE_URL/api/v1/auth/me"  401
check "Actuator metrics não exposto ao público" "$BASE_URL/actuator/metrics" 401

echo
if [[ "$FAILURES" -gt 0 ]]; then
  echo "$FAILURES verificação(ões) falharam."
  exit 1
fi

echo "Todas as verificações passaram."
