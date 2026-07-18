# Evidence Report

## Metadata

- Time: 2026-07-18 18:32:20 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-product-copy-link-admin
- Commit: 1069cac6
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/resources/db/alter-cso-dual-attribution-status-20260716.sql
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
harness/reports/current/latest-product-copy-link-admin-fix.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/scripts/commands/deploy-remote.ps1
harness/scripts/tests/deploy-remote.Tests.ps1
scripts/check-real-pre-schema.sh
~~~

## Owned Git Status

~~~text
M harness/rules/changelog.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Build Result

~~~text
PASS: backend Maven package. PASS: frontend production build. PASS: ProductLibrary/ProductSelectionCard Vitest 30/30. PASS: deployment Pester 8/8. PASS: restored migration and schema-guard helper match their historical source hashes. Full agent-do business preflight was BLOCKED_AUTH only.
~~~

## Docker Status

~~~text
collection failed: time="2026-07-18T18:32:20+08:00" level=warning msg="The \"DB_NAME\" variable is not set. Defaulting to a blank string."
collection failed: time="2026-07-18T18:32:20+08:00" level=warning msg="The \"DB_NAME\" variable is not set. Defaulting to a blank string."
~~~

## Health Check Result

~~~text
PASS: local real-pre backend returned UP and frontend healthz returned 111 107 after restart. PASS: remote backend returned UP and frontend returned ok; both remote containers healthy.
~~~

## Business Validation Result

~~~text
PASS: component coverage proves Copy Brief is available without frontend role inference and emits the conversion request. BLOCKED_AUTH: local real-pre Douyin status reported hasAccessToken=false and hasRefreshToken=false; authenticated real upstream click was not run.
~~~

## Content Maintenance Result

~~~text
State snapshot and changelog updated with deployed SHA 498e1719 and remaining BLOCKED_AUTH risk.
~~~

## Remote Deploy Result

~~~text
PASS: deployed SHA 498e1719bfd96f276820c931f98c3cd51a50e3e9. Remote source head, backend image, frontend image, and both OCI revision labels match exactly. Database migration and read-only schema guard passed before the immutable image switch.
~~~

## Retro Summary

Actionable improvement: deploy preflight now verifies migration includes and helper scripts; retain a single remote deployment lock and require Gitee/GitHub SHA identity before changing the shared remote checkout.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
