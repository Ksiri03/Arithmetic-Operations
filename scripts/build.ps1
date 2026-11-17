$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "env.ps1")

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "build"
$classes = Join-Path $build "classes"

if (Test-Path $build) {
    Remove-Item -Recurse -Force $build
}
New-Item -ItemType Directory -Path $classes -Force | Out-Null

Write-Host "==> Compiling Java sources" -ForegroundColor White
$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host "==> Copying static resources" -ForegroundColor White
Copy-Item (Join-Path $root "src\main\resources\*") $classes -Recurse -Force

Write-Host "==> Packaging executable jar" -ForegroundColor White
& jar --create --file (Join-Path $build "arithmetic-ops.jar") --main-class com.arithmetic.App -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

$size = [math]::Round((Get-Item (Join-Path $build "arithmetic-ops.jar")).Length / 1KB, 0)
Write-Host "==> Built build/arithmetic-ops.jar ($size KB)" -ForegroundColor Green
