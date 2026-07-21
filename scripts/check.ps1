$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

Write-Host "==> Backend"
Push-Location "$Root/apps/api"
try {
    ./mvnw.cmd -B verify
} finally {
    Pop-Location
}

Write-Host "==> Frontend"
Push-Location "$Root/apps/admin"
try {
    if (-not (Test-Path "node_modules")) {
        throw "node_modules ausente. Execute npm install em apps/admin."
    }
    npm run test:ci
    npm run build
} finally {
    Pop-Location
}

Write-Host "==> Checks concluídos"
