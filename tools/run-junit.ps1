$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $javaPath = (Get-Command java).Path
    $env:JAVA_HOME = Split-Path (Split-Path $javaPath -Parent) -Parent
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Running JUnit 5 tests via surefire..."
./mvnw test
