# Retro: DDD-COMPLETE-100-PRODUCT-02

- Issue: #131 商品同步/backfill 异步 job Application 最终收口
- Time: 2026-06-27 20:41 Asia/Shanghai
- Scope: backend product domain

## What Changed

- Added a product Application service as the single API-facing boundary for backfill command/result/status DTOs.
- Kept legacy `ProductActivityBackfillService` as execution detail behind Application.
- Added mapping tests plus an architecture guard so controller cannot re-import legacy backfill/dry-run DTOs.

## Verification Summary

- Clean targeted tests: PASS, 15 tests.
- Clean backend package: PASS.
- real-pre backend rebuild/restart/health: PASS.
- real-pre P0 preflight: PASS.
- Async dry-run job route: PASS for route/status/no-write evidence; data result PARTIAL due bounded one-page dry-run.

## Process Note

`agent-do` was intentionally not executed because the current worktree contained concurrent order/user dirty changes and `git-push-safe` stages every changed file. Manual harness-equivalent steps were executed and recorded instead.

## Harness Change

No harness behavior change. During limits check, `harness/reports` root was already over 50 direct files; old 2026-06-26 root reports were archived to `harness/archive/by-date/report-packages/reports-root-20260626/`.

## Next

Continue product domain with #132: display/status/audit/log policy 收口.
