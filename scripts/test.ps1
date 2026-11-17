$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "env.ps1")

$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "build"
$classes = Join-Path $build "classes"
$testClasses = Join-Path $build "test-classes"

& (Join-Path $PSScriptRoot "build.ps1")

Write-Host "==> Compiling tests" -ForegroundColor White
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
$tests = Get-ChildItem (Join-Path $root "src\test\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -cp $classes -d $testClasses $tests
if ($LASTEXITCODE -ne 0) { throw "test compilation failed with exit code $LASTEXITCODE" }

Write-Host "==> Running calculator test suite" -ForegroundColor White
& java -cp "$classes;$testClasses" com.arithmetic.CalculatorTest
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }

Write-Host "==> All tests passed" -ForegroundColor Green
