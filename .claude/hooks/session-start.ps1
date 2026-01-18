# Session start hook - Check and start Docker services
# This script checks if the infrastructure containers are running and starts them if needed

$requiredServices = @("mongodb", "mysqldb", "keycloak-mysql", "keycloak")

Write-Host "Checking Docker services status..." -ForegroundColor Cyan

# Check if Docker is running
$dockerRunning = docker info 2>$null
if (-not $?) {
    Write-Host "Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Get running containers
$runningContainers = docker compose ps --format "{{.Service}}" 2>$null

$needsStart = $false
foreach ($service in $requiredServices) {
    if ($runningContainers -notcontains $service) {
        Write-Host "Service '$service' is not running" -ForegroundColor Yellow
        $needsStart = $true
    } else {
        Write-Host "Service '$service' is running" -ForegroundColor Green
    }
}

if ($needsStart) {
    Write-Host "`nStarting infrastructure services..." -ForegroundColor Cyan
    docker compose up -d mongodb mysqldb keycloak-mysql keycloak

    Write-Host "`nWaiting for services to be healthy..." -ForegroundColor Cyan
    Start-Sleep -Seconds 5

    # Show final status
    docker compose ps mongodb mysqldb keycloak-mysql keycloak
} else {
    Write-Host "`nAll infrastructure services are already running." -ForegroundColor Green
}
