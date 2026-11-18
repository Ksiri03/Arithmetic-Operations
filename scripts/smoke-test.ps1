param(
    [string]$BaseUrl = "http://localhost:30081"
)

$ErrorActionPreference = "Stop"

Write-Host "==> Checking health at $BaseUrl/api/health" -ForegroundColor White

# A rolling update can briefly leave the NodePort without a ready endpoint.
# Retry the first request instead of failing the whole pipeline on that gap.
$health = $null
for ($attempt = 1; $attempt -le 12; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get -TimeoutSec 10
        if ($health.status -eq "ok") { break }
    } catch {
        $health = $null
        if ($attempt -lt 12) {
            Write-Host "    health attempt $attempt did not answer; retrying in 4 seconds" -ForegroundColor DarkYellow
            Start-Sleep -Seconds 4
        }
    }
}

if ($null -eq $health -or $health.status -ne "ok") {
    throw "Health check failed after retries: $BaseUrl/api/health"
}

Write-Host "    status=$($health.status) instance=$($health.instance) node=$($health.node)" -ForegroundColor Gray

$checks = @(
    @{ a = "128"; b = "64"; op = "add"; expected = "192" },
    @{ a = "128"; b = "64"; op = "subtract"; expected = "64" },
    @{ a = "12"; b = "8"; op = "multiply"; expected = "96" },
    @{ a = "144"; b = "12"; op = "divide"; expected = "12" }
)

$symbols = @{ add = "+"; subtract = "-"; multiply = "*"; divide = "/" }

foreach ($check in $checks) {
    $body = @{ a = $check.a; b = $check.b; op = $check.op }
    $result = Invoke-RestMethod -Uri "$BaseUrl/api/calculate" -Method Post -Body $body -TimeoutSec 20
    if ($result.result -ne $check.expected) {
        throw "$($check.op) failed: expected $($check.expected), got $($result.result)"
    }
    Write-Host "    PASS $($check.a) $($symbols[$check.op]) $($check.b) = $($result.result) [$($result.servedBy)]" -ForegroundColor Gray
}

$history = Invoke-RestMethod -Uri "$BaseUrl/api/history" -Method Get -TimeoutSec 20
if ($history.items.Count -lt 1) { throw "History endpoint returned no items" }

Write-Host "==> Smoke test passed: 4 operations + health + history" -ForegroundColor Green
