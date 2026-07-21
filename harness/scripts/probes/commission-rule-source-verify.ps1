# COMMISSION-RULE-SOURCE-001 read-only verification (real-pre)
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File harness/scripts/probes/commission-rule-source-verify.ps1

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
Set-Location $repoRoot
$compose = @("--env-file", ".env.real-pre", "-f", "docker-compose.real-pre.yml")

Write-Host "=== COMMISSION-RULE-SOURCE-001 verify ==="

Write-Host "`n[1] Git workspace"
git status --short
if ($LASTEXITCODE -ne 0) { throw "git status failed" }

Write-Host "`n[2] Docker services"
docker compose @compose ps --format "table {{.Name}}\t{{.Status}}"

Write-Host "`n[3] SQL probe"
docker compose @compose cp "harness/scripts/probes/commission-rule-source-probe.sql" postgres-real-pre:/tmp/commission-rule-source-probe.sql
docker compose @compose exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/commission-rule-source-probe.sql'

Write-Host "`n[4] Commission config sanity"
docker compose @compose exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SELECT COUNT(*) FROM commission_config WHERE deleted=0;"'
docker compose @compose exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SELECT COUNT(*) FROM commissions WHERE deleted=0;"'

Write-Host "`n[5] Harness limits"
powershell -NoProfile -ExecutionPolicy Bypass -File "harness/scripts/check-harness-limits.ps1"

Write-Host "`n=== DONE (read-only, no business code changed) ==="
