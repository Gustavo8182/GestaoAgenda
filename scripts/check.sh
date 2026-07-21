#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Backend"
(
  cd "$ROOT_DIR/apps/api"
  ./mvnw -B verify
)

echo "==> Frontend"
(
  cd "$ROOT_DIR/apps/admin"
  if [[ ! -d node_modules ]]; then
    echo "node_modules ausente. Execute npm install em apps/admin." >&2
    exit 1
  fi
  npm run test:ci
  npm run build
)

echo "==> Checks concluídos"
