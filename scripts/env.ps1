# Locate a full JDK and put its bin directory first on PATH.
# The Oracle "javapath" shim exposes java/javac but not jar, so we look for
# a complete JDK installation rather than trusting `where java` alone.

$candidates = @()

if ($env:JAVA_HOME) {
    $candidates += $env:JAVA_HOME
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($javaCommand) {
    $javaBin = Split-Path -Parent $javaCommand.Source
    if (Test-Path (Join-Path $javaBin "javac.exe")) {
        $candidates += (Split-Path -Parent $javaBin)
    }
}

foreach ($root in @("C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
    if (Test-Path $root) {
        $candidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $_.FullName }
    }
}

$jdk = $candidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ "bin\javac.exe")) -and (Test-Path (Join-Path $_ "bin\jar.exe"))
} | Select-Object -First 1

if (-not $jdk) {
    throw "A full JDK (17 or newer) was not found. Install a JDK and set JAVA_HOME."
}

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk "bin") + ";" + $env:Path
