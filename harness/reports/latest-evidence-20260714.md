# Latest Evidence — RBAC Phase 1

## Metadata

- Time: 2026-07-14 12:08:39 +08:00
- Environment: local `real-pre`
- Branch: `codex/rbac-implementation-plan`
- Verified code commit: `00e547e4e174a372c94e89651851e1eaf779d9d9`
- Evidence commit: generated after these reports to avoid a commit-reference cycle
- Timestamp evidence: `harness/reports/evidence-20260714-120838.md`
- Deploy remote: `false`

## Result matrix

| Check | Result | Evidence |
| --- | --- | --- |
| Safety | PASS | agent-do real-pre safety check passed |
| Phase 1 focused tests | PASS | 91/91 |
| Related fix tests | PASS | 47/47 |
| Backend compile | PASS | compile completed |
| Backend package | PASS | Maven `-DskipTests package` BUILD SUCCESS |
| Frontend CI/build | PASS | `npm ci` and Vue/Vite production build passed |
| Docker rebuild | PASS | Compose rebuild completed |
| Docker health | PASS | backend/frontend/PostgreSQL/Redis healthy |
| HTTP health | PASS | backend status UP；frontend `/healthz` HTTP 200 |
| Business validation | BLOCKED/PENDING | agent-do exit=1 at preflight；admin login returned HTTP 401 after 5 attempts，未归因产品代码或任何具体凭据内容 |
| Content maintenance | PENDING | agent-do failed before reaching plan stage |
| Database migration | PENDING / NOT EXECUTED | local pre/post: RBAC 4 tables=0, `authz_version` columns=0；remote current schema not verified |
| Runtime activation | PENDING / NOT EXECUTED | dormant foundation only；remote current runtime not verified |
| Remote deployment | NOT EXECUTED | false |
| Harness report limit | FAIL | Phase 1 baseline `89`; first cycle `92`; this cycle `92 -> 94` (+timestamp evidence +retro)；Phase 1 net `+5`; `94 > 50` |

## Database and runtime evidence

- Pre-run read-only SQL: `tables=0`, `authz_column=0`.
- Post-run read-only SQL: `tables=0`, `authz_column=0`.
- Final local Compose state: backend, frontend, PostgreSQL and Redis healthy.
- Local real-pre remained at RBAC 4 tables=0 and `authz_version` columns=0 before and after Harness.
- Migration/runtime activation remain `PENDING / NOT EXECUTED`; the authorization facade remains dormant.
- No remote deployment, database migration, account reset or credential output occurred.
- Remote current schema/runtime was not verified; no remote `0/0` claim is made.

## Business Preflight Detail

- Report: `runtime/qa/out/real-pre-preflight-20260714-120832/report.md`.
- PASS: frontend, backend health, database readiness, reusable mapping and cleanup plan.
- FAIL: admin login after 5 HTTP 401 responses; env guard could not proceed because the admin token was unavailable.
- `BLOCKED_AUTH`: Douyin readiness because the admin token was unavailable.
- Classification: `BLOCKED/PENDING`, never PASS and not attributed to product code or specific credential contents.

## Harness report count facts

- Phase 1 baseline direct files: `89`.
- First evidence cycle: `92`.
- This cycle: `92 -> 94`, adding timestamp evidence and retro (`+2`).
- Phase 1 total net increase: `+5`; final limit is `94 > 50 = FAIL`.
- The files are retained to preserve the evidence chain；this RBAC task performs no cleanup.
- A separate governance task must use manifest-backed archive/cleanup and a stable `current/latest` convention.

## Code Review

- Original `2 Important + 1 Minor`: closed.
- Re-review: `Critical=0`, `Important=0`.
- New remaining Minor: lexical guard may conservatively flag comments/strings；reflection is not its security boundary.

## Conclusion

PARTIAL

The Harness script exited `1` at business preflight. Overall evidence remains `PARTIAL` because completed engineering stages are proven while the business prerequisite is `BLOCKED/PENDING`.

## Remaining risks

- Business preflight remains `BLOCKED/PENDING`; no business PASS is claimed.
- Migration window, shadow runtime plan and business-approved seed matrix remain outstanding.
- Harness direct report count is `94` (baseline `89`, Phase 1 net `+5`)；separate manifest-backed governance is required.
- Frontend audit remains unfixed: 1 low, 1 moderate, 2 high and 2 critical.
- Lexical guard comments/strings false-positive risk remains a Minor；reflection is outside its security boundary.
