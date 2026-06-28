# #158 Frontend Permission Authority Evidence

## Metadata

- Time: 2026-06-28 14:18 +08:00
- Env: local real-pre workspace
- Branch: `feature/ddd/DDD-VERIFY-001`
- Issue: #158 `[DDD-COMPLETE-100-FRONTEND-05] 权限与数据范围 UI 后端权威化`
- Scope: docs/evidence audit; no product code changed in this issue.

## Question

Verify whether frontend route/menu/button/list visibility is an advisory UI layer based on backend user/role/data-scope facts and backend API responses, rather than a replacement for backend authorization.

## Evidence

### User-domain facts enter frontend from backend

- `frontend/src/api/sys.ts` exposes `/users/current`, `/users/current/data-scope`, and `/users/current/permissions/check`.
- `frontend/src/views/profile/UserProfile.vue` calls all three endpoints, displays `dataScope`, and writes the refreshed current user back to `authStore`.
- `frontend/src/stores/auth.ts` normalizes only backend-provided `userInfo.roleCodes`; it does not invent business permissions.

### Route and menu visibility use backend-issued roles

- `frontend/src/router/guard.ts` accepts `roleCodes` from auth state and applies `hasAccess` against route metadata.
- `frontend/src/router/index.ts` hydrates auth state from stored backend login/current-user payload before every navigation.
- `frontend/src/router/menuTree.ts` builds menu visibility from `roleCodes` and drops inaccessible branches.
- Route/menu filtering is UI-only. It is not treated as backend authorization proof.

### API responses remain authoritative

- `frontend/src/utils/requestError.ts` treats HTTP/business `403` as permission denied and routes the server message to global/local permission hints without duplicate toast spam.
- A hidden button or menu does not prove permission. If a user reaches an endpoint directly, the backend response still drives the visible permission result.

### Targeted verification

Command:

```powershell
npm run test -- src/router/guard.test.ts src/router/index.test.ts src/router/menuTree.test.ts src/router/navigation.test.ts src/utils/requestError.test.ts src/api/sys.test.ts src/views/profile/UserProfile.test.ts src/views/layout/Header.test.ts
```

Result:

```text
Test Files: 8 passed
Tests: 71 passed
```

Covered behavior:

- Anonymous and unauthorized route redirects.
- Role-based menu tree filtering and first accessible route resolution.
- User-domain current-user, data-scope and permission-check API wrappers.
- Profile page refresh of current user and data scope.
- Permission denied detection from HTTP/business `403`.
- Global/local permission hint routing without duplicate permission toasts.

## Inventory

| Area | Authority Source | Frontend Role |
| --- | --- | --- |
| Login/current user | `/api/auth/login`, `/api/users/current` payload | Persist/normalize `roleCodes` and `dataScope` |
| Route guard | backend-issued `roleCodes` | Advisory redirect to first accessible frontend route |
| Menu tree | backend-issued `roleCodes` | Hide inaccessible navigation entries |
| Profile data scope | `/api/users/current/data-scope` | Display resolved `self/group/all` and userIds |
| Permission self-check | `/api/users/current/permissions/check` | Display backend allowed/denied result |
| Business APIs | backend endpoint authorization and `403` response | Show server permission hint |

## Conclusion

PASS for #158 V1 frontend scope.

The frontend permission layer is advisory: it consumes backend-issued role/data-scope facts for route/menu/button visibility and relies on backend API authorization plus `403` responses as the final authority. No product code change was required for this issue.

## Residual Risk

- Frontend route/menu allowlists are still static metadata filtered by backend-issued roles; they are not dynamic per-button permission API calls.
- Page-level UI helpers still exist for display/gating. They must remain non-authoritative and cannot replace backend authorization.
- Full browser-level cross-role evidence remains in #159.
