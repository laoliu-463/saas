$ErrorActionPreference = "Stop"

# Run Maven in a one-off backend container (same image/env/volumes as compose "backend").
# Uses `docker compose run` with empty entrypoint so this does not attach to the long-lived
# `spring-boot:run` process and avoids two Maven builds fighting over /app/target.
#
# Usage (from repo root or any cwd — script resolves repo):
#   .\scripts\mvn-test-docker.ps1
#   .\scripts\mvn-test-docker.ps1 -q test
#   .\scripts\mvn-test-docker.ps1 test -Dtest=ColonelSaasApplicationTests
#
# Requires: docker compose stack for this project (postgres/redis reachable on compose network).
# Default env file matches scripts/start-test.ps1.

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $repoRoot "docker-compose.yml"
$envFile = Join-Path $repoRoot ".env.test"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Env file not found: $envFile"
}

$mvnArgs = if ($args.Count -gt 0) { ($args -join " ") } else { "-q test" }

Push-Location $repoRoot
try {
    # Override image ENTRYPOINT (dev-hot-reload.sh) so we only run Maven.
    docker compose --env-file $envFile --project-name saas-active -f $composeFile run --rm --entrypoint /bin/sh backend -c "cd /app && mvn $mvnArgs"
}
finally {
    Pop-Location
}
