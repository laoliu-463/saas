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
| Database migration | PENDING | local read-only check: RBAC 4 tables=0, `authz_version` column=0；本工作流未执行远端 migration/deploy，远端当前 Schema 未核验 |
| Runtime activation | PENDING | dormant foundation only；无 request-path consumer |
| Remote deployment | NOT EXECUTED | false |
| Harness report limit | FAIL | baseline=`89`, final=`92`, net=`+3` required evidence files；未执行硬编码 check 脚本 |

## Database and runtime evidence

- Pre-run read-only SQL: `tables=0`, `authz_column=0`.
- Post-run read-only SQL: `tables=0`, `authz_column=0`.
- PG container changed from `c0fbd9d...1834` to `11f490d...49f`; named volume `saas-active_postgres_real_pre_data` remained attached.
- Verified local/source facts show the RBAC facade remains dormant；legacy role/JWT/data-scope is the known baseline, not a claim about remote current runtime.
- Local real-pre remained at RBAC 4 tables=0 and `authz_version` column=0. This workflow did not execute remote migration or deployment；remote current Schema was not verified.

## Harness report count facts

- Baseline direct files: `89`.
- Final direct files: `92`.
- Required timestamp evidence, latest evidence and retro produced a net task increase of `3`.
- The files are retained to preserve the evidence chain；this RBAC task performs no cleanup.
- A separate governance task must use manifest-backed archive/cleanup and a stable `current/latest` convention.

## Conclusion

PARTIAL

## Remaining risks

- Business preflight remains BLOCKED/PENDING pending secure QA credentials.
- Migration window, shadow runtime plan and business-approved seed matrix remain outstanding.
- Harness direct report count is `92` (baseline `89`, task net `+3`)；separate manifest-backed Harness governance is required.
- Compose dependency recreation and frontend audit findings remain separate follow-up items.
