# Evidence Report

## Metadata

- Time: 2026-07-13 14:25 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Base commit before this task: 2105cf40
- Worktree: dirty before and after; unrelated existing changes preserved
- Remote deploy: not requested, not executed

## Changes

- `frontend/src/views/product/components/ProductEditModal.vue`
- `frontend/src/views/product/components/ProductEditModal.test.ts`
- Right-side drawer fields reduced to exclusive-price status, exclusive-price remark, ad-support flag, reward remark, participation requirements, start time and end time.
- Start/end time are read-only snapshot facts; hand-card, tags, script, selling points and remark inputs were removed.

## Verification

| Check | Result | Evidence |
|---|---|---|
| Component test | PASS | 3/3 `ProductEditModal` tests |
| Frontend test suite | PASS | 92 files, 692 tests |
| Typecheck | PASS | `npm --prefix frontend run typecheck` |
| Frontend build | PASS | `npm --prefix frontend run build` |
| Docker rebuild/restart | PASS after retry | First attempt hit a Docker name conflict; retry rebuilt and started all four real-pre services |
| Local health | PASS | `verify-local.ps1 -Env real-pre -Scope frontend`; frontend `/healthz` HTTP 200; compose services healthy |
| real-pre preflight | FAIL/BLOCKED | `runtime/qa/out/real-pre-preflight-20260713-142240/report.md`; admin login HTTP 401, admin token unavailable |
| Product edit API/E2E | BLOCKED | No admin token, so authenticated product-save smoke was not executed |

## Conclusion

PARTIAL. The frontend drawer refactor is build- and unit-tested and is loaded by the local real-pre frontend container. Authenticated real-pre business verification is blocked by the existing admin login 401; no claim is made that the live product-save request has passed.

## Residual risks

- The edit API call remains unverified against an authenticated real-pre session.
- Start/end time and exclusive-price status are displayed from existing product data and are not submitted as editable fields.
- Existing unrelated dirty files were not staged or modified.
