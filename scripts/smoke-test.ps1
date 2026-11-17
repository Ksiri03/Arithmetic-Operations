param(
    [string]$BaseUrl = "http://localhost:30081"
)

$ErrorActionPreference = "Stop"

Write-Host "==> Checking health at $BaseUrl/api/health" -ForegroundColor White
$health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get -TimeoutSec 20
if ($health.status -ne "ok") { throw "Health check failed: $($health | ConvertTo-Json -Compress)" }
Write-Host "    status=$($health.status) instance=$($health.instance) node=$($health.node)" -ForegroundColor Gray

$checks = @(
    @{ a = "128"; b = "64"; op = "add"; expected = "192" },
    @{ a = "128"; b = "64"; op = "subtract"; expected = "64" },
    @{ a = "12"; b = "8"; op = "multiply"; expected = "96" },
    @{ a = "144"; b = "12"; op = "divide"; expected = "12" }
)

foreach ($check in $checks) {
    $body = @{ a = $check.a; b = $check.b; op = $check.op }
    $result = Invoke-RestMethod -Uri "$BaseUrl/api/calculate" -Method Post -Body $body -TimeoutSec 20
    if ($result.result -ne $check.expected) {
        throw "$($check.op) failed: expected $($check.expected), got $($result.result)"
    }
    Write-Host "    PASS $($result.expression) [$($result.servedBy)]" -ForegroundColor Gray
}

$history = Invoke-RestMethod -Uri "$BaseUrl/api/history" -Method Get -TimeoutSec 20
if ($history.items.Count -lt 1) { throw "History endpoint returned no items" }

Write-Host "==> Smoke test passed: 4 operations + health + history" -ForegroundColor Green
