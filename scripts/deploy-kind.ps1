param(
    [string]$ClusterName = "arithmetic-lab",
    [string]$ImageTag = "latest"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Assert-LastExitCode([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed with exit code $LASTEXITCODE" }
}

Push-Location $root
try {
    $clusters = & kind get clusters 2>$null
    if ($LASTEXITCODE -ne 0) { $clusters = @() }
    if ($clusters -notcontains $ClusterName) {
        Write-Host "==> Creating kind cluster '$ClusterName'" -ForegroundColor White
        & kind create cluster --name $ClusterName --config "k8s\kind-config.yaml"
        Assert-LastExitCode "kind create cluster"
    } else {
        Write-Host "==> Reusing kind cluster '$ClusterName'" -ForegroundColor Gray
    }

    Write-Host "==> Building image arithmetic-ops:$ImageTag" -ForegroundColor White
    & docker build -t "arithmetic-ops:$ImageTag" -t "arithmetic-ops:latest" .
    Assert-LastExitCode "docker build"

    Write-Host "==> Loading image into kind" -ForegroundColor White
    & kind load docker-image "arithmetic-ops:$ImageTag" "arithmetic-ops:latest" --name $ClusterName
    Assert-LastExitCode "kind load"

    Write-Host "==> Applying Kubernetes manifests" -ForegroundColor White
    & kubectl apply -k k8s
    Assert-LastExitCode "kubectl apply"

    & kubectl -n arithmetic set image deployment/arithmetic-app "app=arithmetic-ops:$ImageTag"
    Assert-LastExitCode "kubectl set image"

    Write-Host "==> Waiting for rollout" -ForegroundColor White
    & kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
    Assert-LastExitCode "kubectl rollout"

    Write-Host "==> Running smoke test" -ForegroundColor White
    & (Join-Path $PSScriptRoot "smoke-test.ps1") -BaseUrl "http://localhost:30081"
    Assert-LastExitCode "smoke test"

    Write-Host ""
    Write-Host "Application is live at http://localhost:30081" -ForegroundColor Green
    Write-Host "Pods:" -ForegroundColor Gray
    & kubectl -n arithmetic get pods -o wide
} finally {
    Pop-Location
}
