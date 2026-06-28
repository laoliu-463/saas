# #155 Frontend RBAC Boundary Cleanup Evidence

## Metadata

- Time: 2026-06-28 13:49 +08:00
- Environment: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `6bccb1ae`
- Issue: #155 `[DDD-COMPLETE-100-FRONTEND-02]`
- Selected Gate: Gate 3
- Remote deploy: not requested

## Scope

- Main area: frontend RBAC UI adapter and display gating.
- DDD layer mapping: User Domain provides authority; frontend keeps API/UI display adapter only.
- Modified frontend files:
  - `frontend/src/constants/rbac.ts`
  - `frontend/src/stores/auth.ts`
  - `frontend/src/views/product/index.vue`
  - `frontend/src/views/product/ProductDetail.vue`
  - `frontend/src/views/product/ActivityList.vue`
  - `frontend/src/views/talent/index.vue`
  - `frontend/src/views/talent/constants.ts`
  - `frontend/src/views/sample/sample-permissions.ts`
- Tests updated:
  - `frontend/src/constants/rbac.test.ts`
  - `frontend/src/views/sample/sample-permissions.test.ts`
  - `frontend/src/views/talent/constants.test.ts`

## Evidence Chain

### Phenomenon

Frontend UI role checks were split across `stores/auth.ts`, product pages, talent pages and sample helpers. Some checks used raw role strings such as `biz_staff`, `channel_leader` and `admin`.

### Evidence

- `rg` inventory found local legacy role mapping in `frontend/src/stores/auth.ts`.
- `rg` inventory found direct role string checks in product, activity and talent views.
- GitHub #155 was already closed before this code slice; this report records the concrete code cleanup and validation performed after that closure.

### Change

- Moved legacy role code mapping into `frontend/src/constants/rbac.ts`.
- Added `normalizeRoleCodes`, `hasAnyRole`, `isLeaderRole`.
- Made `hasAccess` normalize current and required roles.
- Updated `auth` store to consume RBAC helpers instead of owning compatibility rules.
- Replaced selected page-level raw role string checks with `ROLE_CODES` and RBAC helpers.
- Added comments that RBAC helpers are UI visibility only; backend user domain remains authoritative.

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph | PASS | graph stats loaded; change detection risk was high because RBAC is a hub; direct RBAC tests added |
| Whitespace | PASS | `git diff --check` |
| Targeted frontend tests | PASS | 6 files / 40 tests |
| Frontend build | PASS | `npm run build` |
| Harness full | PASS | `agent-do.ps1 -Env real-pre -Scope full` |
| Backend package | PASS | `mvn -f backend/pom.xml -DskipTests package` via harness |
| Container restart | PASS | backend/frontend real-pre recreated |
| Health | PASS | backend `/api/system/health` 200 UP, frontend `/healthz` 200 |
| Business preflight | PASS | `npm run e2e:real-pre:p0:preflight`, output `runtime/qa/out/real-pre-preflight-20260628-134900` |

Targeted frontend test command:

```powershell
npm run test -- src/constants/rbac.test.ts src/views/sample/sample-permissions.test.ts src/views/talent/constants.test.ts src/router/guard.test.ts src/router/navigation.test.ts src/views/product/ActivityList.test.ts
```

Result: 6 test files passed, 40 tests passed.

## Boundary Result

- Frontend no longer owns historical role code compatibility in `auth` store.
- Product/talent/sample selected UI gating consumes centralized RBAC helpers.
- No backend permission rule, data scope rule, DB schema, API contract, or state machine was changed.

## Residual Risk

- Product and sample status helper files still contain UI display mappings. They are display/gating only and must not be treated as business state machine authority.
- #158 remains the deeper task for permission and data-scope UI backend-authoritative alignment.
- #156/#157 remain open for page/API domain alignment.

## Conclusion

PASS for this #155 code cleanup slice. Remaining frontend domain work is tracked by #156-#159.
