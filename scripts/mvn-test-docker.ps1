$ErrorActionPreference = "Stop"

# Run Maven in a one-off Maven container on the test compose network.
# This uses the shared test PostgreSQL/Redis services instead of localhost.
# The backend dev runtime image is JRE-only, so Maven runs from a dedicated Maven image.
#
# Usage (from repo root or any cwd — script resolves repo):
#   .\scripts\mvn-test-docker.ps1
#   .\scripts\mvn-test-docker.ps1 -q test
#   .\scripts\mvn-test-docker.ps1 test "-Dtest=ColonelSaasApplicationTests"
#   .\scripts\mvn-test-docker.ps1 test -Dtest=ColonelSaasApplicationTests
#
# Requires: docker compose stack for this project (postgres/redis reachable on compose network).
# Default env file matches scripts/start-test.ps1.

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $repoRoot "docker-compose.test.yml"
$realPreComposeFile = Join-Path $repoRoot "docker-compose.real-pre.yml"
$envFile = Join-Path $repoRoot ".env.test"
$projectName = "saas-test"
$mavenImage = if ($env:MAVEN_DOCKER_IMAGE) { $env:MAVEN_DOCKER_IMAGE } else { "maven:3.9.9-eclipse-temurin-17" }

. (Join-Path $scriptDir "stack-utils.ps1")

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $realPreComposeFile)) {
    throw "Real-pre compose file not found: $realPreComposeFile"
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Env file not found: $envFile"
}

$mvnArgs = if ($args.Count -gt 0) { ($args -join " ") } else { "-q test" }
$envMap = Read-EnvFile -Path $envFile
$dbPassword = if ($env:DB_PASSWORD) {
    $env:DB_PASSWORD
} elseif ($envMap.ContainsKey("DB_PASSWORD") -and $envMap["DB_PASSWORD"]) {
    $envMap["DB_PASSWORD"]
} else {
    "saas123"
}
$redisPassword = if ($env:REDIS_PASSWORD) {
    $env:REDIS_PASSWORD
} elseif ($envMap.ContainsKey("REDIS_PASSWORD")) {
    $envMap["REDIS_PASSWORD"]
} else {
    ""
}

Push-Location $repoRoot
try {
    docker compose --env-file $envFile --project-name $projectName -f $composeFile up -d --wait postgres redis
    Assert-LastExitCode -CommandName "docker compose up postgres redis"

    # Run Maven in a dedicated Maven image on the same compose network.
    # The backend dev runtime image is JRE-only and intentionally has no Maven.
    docker run --rm `
        --network "${projectName}_default" `
        --volume "$(Join-Path $repoRoot "backend"):/app" `
        --volume "${realPreComposeFile}:/docker-compose.real-pre.yml:ro" `
        --volume "maven_cache_test:/root/.m2" `
        --workdir /app `
        --env SPRING_PROFILES_ACTIVE=test `
        --env DB_HOST=postgres `
        --env DB_PORT=5432 `
        --env DB_NAME=colonel_saas_test `
        --env DB_USER=saas `
        --env DB_PASSWORD="$dbPassword" `
        --env REDIS_HOST=redis `
        --env REDIS_PORT=6379 `
        --env REDIS_PASSWORD="$redisPassword" `
        --env REDIS_DATABASE=1 `
        $mavenImage sh -lc "mvn $mvnArgs"
    Assert-LastExitCode -CommandName "docker run maven"
}
finally {
    Pop-Location
}
