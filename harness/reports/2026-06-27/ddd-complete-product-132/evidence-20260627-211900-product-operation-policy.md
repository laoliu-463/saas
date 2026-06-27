# Evidence Report - #132 Product Operation Policy

## Metadata

- Time: 2026-06-27 21:19 +08:00
- Environment: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Commit verified: 2e93a069
- Remote deploy: false
- Worktree note: main worktree still had concurrent frontend dirty files; runtime images were built from clean detached worktree `D:\Projects\SAAS-clean-2e93` at 2e93a069.

## Scope

- Issue: #132 `[DDD-COMPLETE-100-PRODUCT-03]`
- Target: move product display/status/audit/operation-log semantics out of legacy service/controller and into product policy/application seams.
- Runtime behavior changed in prior product commits; this report verifies current HEAD after the user-test compile fix.

## Code Evidence

- Added `ProductOperationDecisionPolicy` for library entry, activity bind, assign, audit owner assign and progress decision log/status semantics.
- `ProductService` now delegates operation payload, labels, remarks and decision normalization to product policies.
- Architecture guard `DddProduct003ProductRoutingTest` checks policy delegation and blocks key legacy inline strings from returning to `ProductService`.

## Verification

- code-review-graph: #132 files analyzed; risk score 0.15.
- Backend targeted tests: `mvn "-Dtest=ProductOperationDecisionPolicyTest,DddProduct003ProductRoutingTest,ProductServiceFilterTest" test` PASS, 35 tests.
- User test compile blocker fixed and verified: `mvn "-Dtest=CurrentUserApplicationServiceTest" test` PASS, 4 tests.
- Backend package: `mvn -DskipTests package` PASS in main worktree and clean worktree.
- Frontend product display unit tests: `pnpm vitest run src/views/product/product-library-display.test.ts` PASS, 53 tests.
- Frontend build: first failed because `node_modules/.pnpm/lodash-es@4.18.1` was incomplete; after `pnpm install --force --frozen-lockfile`, `pnpm build` PASS.

## Real-pre Runtime

- Built `colonel-saas/backend:real-pre` from clean worktree backend jar at 2e93a069.
- Built `colonel-saas/frontend:real-pre` from clean worktree frontend at 2e93a069.
- Restarted with `docker compose -f docker-compose.real-pre.yml up -d --no-build backend-real-pre frontend-real-pre`.
- Docker status: backend and frontend containers healthy.
- HTTP health: `GET /api/system/health` returned `{"status":"UP"}`; frontend `/healthz` returned 200 content.
- P0 preflight: `npm run e2e:real-pre:p0:preflight` PASS; output `runtime/qa/out/real-pre-preflight-20260627-211719`.

## DDD Metrics

- Production Java LOC: 76,431
- DDD domain LOC: 16,424
- Raw domain share: 21.5%
- Business migration proxy: 28.3%
- Product proxy: 27.1%

## Conclusion

PASS for #132 policy/application boundary and local real-pre runtime verification.

## Residual Risk

- Real promotion-link order `pick_source` positive return sample is still PENDING and remains tracked by #135.
- Main worktree had concurrent frontend dirty files not included in this evidence or runtime images.
