$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "env.ps1")

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "build\arithmetic-ops.jar"

if (-not (Test-Path $jar)) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

if (-not $env:PORT) {
    $env:PORT = "8080"
}

Write-Host "Starting Arithmetic Operations on http://localhost:$env:PORT" -ForegroundColor Green
& java -jar $jar
