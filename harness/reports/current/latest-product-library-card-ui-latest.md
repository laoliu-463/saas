# Evidence Report

## Metadata

- Time: 2026-07-18 18:28:43 +08:00
- Environment: real-pre
- Scope: frontend-only remote release after full deploy gate failure
- Branch: codex/product-library-card-ui-latest
- Evidence base commit: 26e260bc104fd82a092120752dea675fb5b13938
- Final deployed frontend image revision: 498e1719bfd96f276820c931f98c3cd51a50e3e9
- Frontend source revision: 2a0e51c57a1b61b5b91897f1bed5edbcd4a98121
- Deploy remote: true

## Owned Files

~~~text
frontend/src/components/product/ProductSelectionCard.test.ts
frontend/src/components/product/ProductSelectionCard.vue
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
frontend/src/views/product/product-library-layout.test.ts
frontend/src/views/product/product-library-layout.ts
~~~

No frontend application files changed between source revision `2a0e51c5` and the latest concurrent branch baseline `498e1719`.

## Build Result

~~~text
Backend package: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend clean install: PASS (npm --prefix frontend ci)
Frontend production build: PASS (npm --prefix frontend run build)
Frontend source typecheck: PASS (npm --prefix frontend run typecheck)
Frontend production asset: ProductLibrary-DCjlnU9S.js
~~~

## Local Docker And Health

~~~text
agent-do local compose rebuild: PASS
backend-real-pre: HTTP 200, {"status":"UP"}
frontend-real-pre: HTTP 200, ok
postgres-real-pre: healthy
redis-real-pre: healthy
~~~

## Business Validation

~~~text
Targeted product-card tests: PASS, 3 files / 31 tests
Full frontend tests: PASS, 105 files / 764 tests
Default real-pre preflight: BLOCKED_AUTH
  - frontend HTTP: PASS
  - backend health: PASS
  - admin login: PASS
  - environment guard: PASS
  - database schema readiness: PASS
  - Douyin token readiness: BLOCKED_AUTH (no access/refresh token)
~~~

## Remote Deploy Result

~~~text
Fixed full deploy attempt 1: FAIL, SSH process returned -1 during remote Maven testCompile.
Fixed full deploy attempt 2: FAIL, exit 3 because 99-migrate-all.sql referenced missing alter-cso-dual-attribution-status-20260716.sql.
Immutable frontend image build: PASS.
Initial scoped frontend-real-pre switch: PASS at revision 7d71a8c343f9c28b1c8a0671f792ba1b0190a039.
Backend was restored to its pre-task image immediately after that scoped switch.
Concurrent permission/deployment work subsequently completed the repaired full deploy at revision 498e1719bfd96f276820c931f98c3cd51a50e3e9.
Final backend running image: colonel-saas/backend:498e1719bfd96f276820c931f98c3cd51a50e3e9.
Final frontend running image: colonel-saas/frontend:498e1719bfd96f276820c931f98c3cd51a50e3e9.
Final frontend revision label: 498e1719bfd96f276820c931f98c3cd51a50e3e9.
Remote backend health: HTTP 200, {"status":"UP"}.
Remote frontend health: HTTP 200, ok.
Postgres was not recreated by the scoped frontend switch.
~~~

The missing migration and schema-guard helper were restored concurrently in later branch commits `43a2ec3f` and `498e1719`. Those fixes were deployed by the concurrent permission/deployment task. There are no frontend application-file changes between this task's source revision `2a0e51c5` and the final deployed revision `498e1719`.

## Remote Headless DOM Verification

~~~json
{
  "url": "http://1.14.108.159/product/library",
  "cardCount": 100,
  "gridGap": "8px",
  "titleOverflowCount": 0,
  "titleMismatchCount": 0,
  "idOverflowCount": 0,
  "missingIdCount": 0,
  "bodyOverflowCount": 0,
  "contentButtonTestIds": ["product-copy-id"],
  "copyIdCount": 100,
  "copyBriefCount": 100,
  "obsoleteButtonCount": 0
}
~~~

Production bundle inspection also confirmed `product-copy-id` and `product-id-value` are present, while `product-copy-url`, `product-refresh`, and `product-detail-btn` are absent.

## Content Maintenance Result

Content maintenance skipped by `-ContentMaintenance off`; this task owns only the six frontend files listed above and this current evidence report.

## Retro Summary

Remote branch drift caused the earlier UI rollback. Future UI deployments must rebase onto and verify the latest `feature/auth-system` head before release. When a frontend-only release is required, Compose must use `--no-deps` to prevent dependency services from being recreated; the first scoped switch omitted it, recreated the backend once, and the backend was immediately restored to its prior immutable image before validation. The concurrent deployment task later moved both services to repaired revision `498e1719`.

## Conclusion

PARTIAL

The requested frontend is deployed and verified on remote real-pre. The result remains `PARTIAL`, not `PASS`, because the repository's fixed full deployment path failed before the frontend-only scoped switch and the default real-pre preflight is blocked by missing Douyin authorization.

## Residual Risk

- Copy-introduction permission behavior was not modified or functionally exercised in this task; only the retained entry and disabled/loading unit states were verified.
- `npm run typecheck:test` still has three pre-existing typing errors in `src/views/product/components/QuickSampleModal.test.ts`; application typecheck and production build pass.
- Final remote revision `498e1719` includes concurrent deployment-script/database repairs. This task did not author those changes; verification here is limited to final container revisions, health, frontend bundle markers, and product-library DOM.
