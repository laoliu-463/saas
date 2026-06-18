# Evidence Report - 2026-06-18 CI/CD And Real-Pre Deployment

- Time: 2026-06-18 16:12:00 +08:00
- Env: local real-pre and remote real-pre server
- Branch: feature/ddd/DDD-VERIFY-001
- Commit: 96700af8
- Worktree clean: no
- Worktree note: unrelated local changes remain in auth/login/request files and old harness report archive moves; they were not staged in this task.
- Remote deployed: yes
- Conclusion: PENDING

## Scope

- Implemented and pushed CI/CD pipeline work on this branch.
- Performed controlled remote real-pre deployment with fixed deploy script.
- Fixed one frontend permission-boundary issue found by roles E2E.
- Fixed roles E2E to use async activity product sync instead of synchronous refresh GET.
- Validated remote public ingress, roles flow, and real-pre P0 gate.

## Commits

- fd15f448 `ci: add gated Jenkins pipeline`
- 97a1b7a8 `deploy: harden real-pre preflight guard`
- f3089535 `docs: record real-pre deploy blocker`
- 9e403b34 `test: default playwright to headless`
- 3066c0b7 `fix: skip admin-only douyin lookup for scoped roles`
- 96700af8 `test: avoid synchronous activity product refresh in roles e2e`

## Local Validation

- `npx --yes pnpm@9 test -- ActivityList.test.ts`: PASS, 2 tests.
- `npx --yes pnpm@9 build`: PASS.
- `npx --yes pnpm@9 exec playwright test tests/e2e/11-real-pre-role-business-flow.spec.ts --list`: PASS, 1 test listed.
- Build warning: Vite still reports chunks larger than 500 kB; this is an existing warning, not a failed gate.

## Remote Env Update

- Server app dir: `/opt/saas/app`.
- Remote branch fast-forwarded to 96700af8 from gitee.
- Remote `.env.real-pre` non-secret public URL values were updated to public HTTP port 80.
- Env backup: `/opt/saas/env/.env.real-pre.bak-20260618-150124-before-public80`.
- Public ingress observed: `http://1.14.108.159` works via Nginx.
- Public HTTPS/443 observed: not reachable in this validation window.

## Remote Deployment

- Deploy script: `scripts/deploy-real-pre.sh`.
- Latest evidence dir: `/opt/saas/runtime/qa/out/deploy-real-pre-20260618-async-roles-production-validation`.
- Deployment result: PASS.
- DB backup: `/opt/saas/backups/saas_real_pre-20260618-160023.dump`.
- Image tag: 96700af8.
- Containers after deployment:
  - `saas-active-frontend-real-pre-1` -> `colonel-saas/frontend:96700af8`, healthy.
  - `saas-active-backend-real-pre-1` -> `colonel-saas/backend:96700af8`, healthy.
  - PostgreSQL and Redis healthy.

## Health Checks

- Public frontend health: `http://1.14.108.159/healthz` -> 200.
- Public backend health: `http://1.14.108.159/api/system/health` -> 200, `{"status":"UP"}`.
- Public Kuaidi100 callback reachability: POST `/api/public/logistics/kuaidi100/callback` -> 200 with missing-parameter response, as expected for unsigned probe.
- Remote preflight before roles: PASS.
- Roles preflight evidence: `/opt/saas/app/runtime/qa/out/real-pre-preflight-20260618-160231`.

## E2E Results

- `npm run e2e:real-pre:roles`: PASS after fixes.
- Roles validated menus, permissions and business handoffs for admin, biz_leader, merchant, channel, operator and channel_leader.
- Initial roles blocker fixed:
  - non-admin `/product/manage` no longer calls admin-only `/api/douyin/institution-info`.
- Secondary roles blocker fixed in harness:
  - roles now triggers `POST /products/sync` and polls DB view instead of using long synchronous `GET ...?refresh=true`.

## P0 Result

- First P0 attempt after deployment: FAIL due missing Playwright ffmpeg binary.
- Runtime dependency fix: `npx playwright install ffmpeg`, installed `/home/deploy/.cache/ms-playwright/ffmpeg-1011`.
- Re-run: `npm run e2e:real-pre:p0`.
- P0 evidence dir: `/opt/saas/app/runtime/qa/out/real-pre-p0-20260618-160815`.
- Final P0 status: PENDING.

P0 step status:

- preflight: PASS.
- 08 Douyin integration: PASS.
- 31 product chain: PASS.
- 32 order attribution: PENDING.
- 33 sample chain: PENDING.
- 34 performance dashboard: PENDING.
- 35 RBAC: PASS.
- 36 cleanup plan: PASS_NEEDS_CLEANUP.

PENDING reasons from report:

- `PENDING_NO_UPSTREAM_ORDERS`: current real orders are not attributed, so attribution chain cannot be proven.
- `PENDING_NO_ASSIGNED_SAMPLE_PRODUCT`: no real in-library product assigned for biz_staff sample audit chain.
- `PENDING_NO_PERFORMANCE_SAMPLE`: no readable performance sample for dashboard formula validation.

## Safety And Boundaries

- No secret values were printed or committed.
- Backend admin-only Douyin endpoint permission was not relaxed.
- Frontend fix stays in presentation/request boundary: non-admin page skips admin-only institution lookup.
- Real Douyin upstream mode remained live.
- `APP_TEST_ENABLED` and `DOUYIN_TEST_ENABLED` remained false in real-pre preflight.

## Not Fully Verified

- Live Jenkins controller execution was not available in this workspace.
- P0 cannot be declared PASS because it is waiting on real data prerequisites.
- Production-domain HTTPS readiness was not proven; validation used public HTTP IP.
- Cleanup plan generated by step 36 still needs review/execution before declaring the test window fully cleaned.

## Conclusion

PENDING.

The deployment mechanism, public HTTP ingress, container health, roles E2E, Douyin integration, product chain and RBAC are verified on remote real-pre at commit 96700af8. This is not yet a full production-applicable PASS because P0 still depends on missing real order, sample and performance data, and HTTPS/domain readiness was not verified.
