$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path "$Root/.env")) {
    Copy-Item "$Root/.env.example" "$Root/.env"
    Write-Host "Arquivo .env criado. Troque a senha antes de produção."
}

docker compose --file "$Root/compose.yaml" up -d

Push-Location "$Root/apps/admin"
try {
    npm install
} finally {
    Pop-Location
}

Write-Host "Bootstrap concluído. Consulte README.md para iniciar API e painel."
