param(
    [string]$BaseUrl = "http://localhost:8080"
)

# Smoke test mínimo para qualquer ambiente já no ar (local, homologação ou produção).
# Não builda nada, não depende de código-fonte — só confirma que a API está de pé,
# respondendo e com a segurança básica esperada. Uso: ./scripts/smoke-test.ps1 -BaseUrl https://...

$failures = 0

function Test-Endpoint {
    param(
        [string]$Description,
        [string]$Url,
        [int]$ExpectedStatus
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing
        $status = [int]$response.StatusCode
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        } else {
            $status = 0
        }
    } catch {
        $status = 0
    }

    if ($status -eq $ExpectedStatus) {
        Write-Host "OK   $Description ($status)"
    } else {
        Write-Host "FAIL $Description — esperado $ExpectedStatus, recebido $status"
        $script:failures++
    }
}

Write-Host "Smoke test contra: $BaseUrl"
Write-Host ""

Test-Endpoint -Description "Health check" -Url "$BaseUrl/actuator/health" -ExpectedStatus 200
Test-Endpoint -Description "Status da API" -Url "$BaseUrl/api/v1/system/status" -ExpectedStatus 200
Test-Endpoint -Description "Endpoint protegido nega sem sessão" -Url "$BaseUrl/api/v1/auth/me" -ExpectedStatus 401

Write-Host ""
if ($failures -gt 0) {
    Write-Host "$failures verificação(ões) falharam."
    exit 1
}

Write-Host "Todas as verificações passaram."
