param(
    [switch]$Run,
    [string]$Port = "8090",
    [string]$Name = "watchdog"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path "$PSScriptRoot\..").Path

Write-Host "`n[1/2] Building JAR..." -ForegroundColor Cyan
mvn package -DskipTests -B --no-transfer-progress
if ($LASTEXITCODE -ne 0) { Write-Host "Maven build failed." -ForegroundColor Red; exit 1 }

Write-Host "`n[2/2] Building Docker image..." -ForegroundColor Cyan
docker build -f "$Root\.docker\Dockerfile" -t watchdog:latest $Root
if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed." -ForegroundColor Red; exit 1 }

Write-Host "`nDone. Image: watchdog:latest" -ForegroundColor Green

if ($Run) {
    Write-Host "`nStarting container on port $Port..." -ForegroundColor Cyan
    docker run --rm -d --name $Name -p "${Port}:${Port}" -e WATCHDOG_PORT=$Port watchdog:latest
}