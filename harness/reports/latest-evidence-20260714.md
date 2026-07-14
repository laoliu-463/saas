# Latest Evidence — RBAC Phase 1

## Metadata

- Time: 2026-07-14 11:07:50 +08:00
- Environment: local `real-pre`
- Branch: `codex/rbac-implementation-plan`
- Commit at verification/report generation: `3d7a7a6f`
- Worktree: isolated worktree; final evidence commit pending at report generation
- Timestamp evidence: `harness/reports/evidence-20260714-110352.md`
- Deploy remote: `false`

## Result matrix

| Check | Result | Evidence |
| --- | --- | --- |
| Safety | PASS | agent-do real-pre safety check passed |
| Phase 1 focused tests | PASS | reviewed Surefire evidence: 78/78, 0 failures/errors/skips |
| Phase 1 compile | PASS | reviewed evidence；本次 package 经过 compile |
| Backend package | PASS | Maven `-DskipTests package` BUILD SUCCESS |
| Frontend CI/build | PASS | `npm ci` and Vue/Vite production build passed |
| Docker rebuild | PASS | backend/frontend rebuilt；Compose also recreated PG dependency while preserving its named volume |
| Docker health | PASS | backend/frontend/PostgreSQL/Redis healthy |
| HTTP health | PASS | backend status UP；frontend `/healthz` HTTP 200 |
| Business validation | BLOCKED/PENDING | preflight admin login HTTP 401；缺少非回显 QA 凭据，未验证业务，不归因产品功能 |
| Content maintenance | PENDING | agent-do failed before reaching plan stage |
| Database migration | PENDING | not authorized / not applied |
| Runtime activation | PENDING | dormant foundation only；无 request-path consumer |
| Remote deployment | NOT EXECUTED | false |
| Harness report limit | FAIL | direct files = `92` (>50)；未执行硬编码 check 脚本 |

## Database and runtime evidence

- Pre-run read-only SQL: `tables=0`, `authz_column=0`.
- Post-run read-only SQL: `tables=0`, `authz_column=0`.
- PG container changed from `c0fbd9d...1834` to `11f490d...49f`; named volume `saas-active_postgres_real_pre_data` remained attached.
- Current runtime remains legacy role/JWT/data-scope；RBAC facade remains dormant.
- Local or remote RBAC migration was not executed.

## Conclusion

PARTIAL

## Remaining risks

- Business preflight remains BLOCKED/PENDING pending secure QA credentials.
- Migration window, shadow runtime plan and business-approved seed matrix remain outstanding.
- Harness direct report count exceeds the policy limit；separate Harness governance is required.
- Compose dependency recreation and frontend audit findings remain separate follow-up items.
