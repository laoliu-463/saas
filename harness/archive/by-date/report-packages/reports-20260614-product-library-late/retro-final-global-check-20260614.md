# Retro Summary

- task: FINAL-GLOBAL-CHECK-AND-REMOTE-DEPLOY-001
- time: 2026-06-14 15:40 Asia/Shanghai
- conclusion: harness upgraded

## What Changed

- `deploy-remote.ps1` was fixed so non-SQL `docker compose exec -T` checks no longer consume the SSH script stdin.
- This fixed a false-positive remote deploy path where the script stopped after the postgres readiness step while SSH still returned success.

## Evidence

- final remote deploy reached schema guard, Maven build, jar guard, compose rebuild, backend health, frontend health, and product sync env checks.
- evidence report: `harness/reports/remote-deploy-20260614.md`

## Follow-up

- Add a regression guard for `deploy-remote.ps1` dry-run output to require stdin-safe compose exec calls.
- Keep remote business PASS separate from deploy PASS; do not collapse PENDING data prerequisites into PASS.
