# DDD Acceptance Latest Report

## Metadata

- Time: 2026-07-12 14:16:51 +08:00
- Branch: codex/ddd-user-role-application
- HEAD: 92eb547f
- Conclusion: PASS

## Dirty Files

- Total: 14
- docs/harness: 13
- known historical tests: 0
- unexpected non docs/harness: 1

~~~text
 M backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncIntegrationClosureContractTest.java
 M docs/ddd-completion-evidence-matrix.md
 D harness/reports/evidence-20260712-013336.md
 D harness/reports/evidence-20260712-135645.md
 M harness/reports/latest-ddd-acceptance-report.md
 D harness/reports/latest-evidence-20260704.md
 D harness/reports/latest-evidence-20260707.md
 D harness/reports/retro-20260704-142252.md
 D harness/reports/retro-20260704-143955.md
 D harness/reports/retro-20260704-150831.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
?? harness/manifests/reports-cleanup-20260712.json
?? harness/reports/evidence-20260712-141015.md
?? harness/reports/retro-20260712-135818.md
~~~

## Whitelist

| File | Active | Total Lines |
| --- | ---: | ---: |
| cross-domain-mapper-legacy-whitelist.txt | 0 | 10 |
| architecture-redline-legacy-whitelist.txt | 0 | 3 |

## Matrix

| DONE | PARTIAL | TODO | BLOCKED | Total |
| ---: | ---: | ---: | ---: | ---: |
| 139 | 31 | 0 | 8 | 178 |

## Checks

| Check | Status | Summary | Command |
| --- | --- | --- | --- |
| git status | WARN | dirtyFiles=14 | git status --short |
| cross-domain mapper whitelist | PASS | active=0, totalLines=10 |  |
| architecture redline whitelist | PASS | active=0, totalLines=3 |  |
| DDD evidence matrix | PASS | DONE=139, PARTIAL=31, TODO=0, BLOCKED=8, total=178 |  |
| git diff --check | PASS | exitCode=0 | git diff --check |
| check-harness-limits | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 |
| safety-check docs dry-run | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun |
| mvn compile | PASS | exitCode=0 | cd backend; mvn -DskipTests compile |
| DddArchitectureRedlineGuardTest | PASS | exitCode=0 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| DddArchitectureRedlineGuardTest surefire | PASS | tests=4, failures=0, errors=0, skipped=0, files=1 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| wide DDD architecture tests | PASS | exitCode=0 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |
| wide DDD architecture tests surefire | PASS | tests=366, failures=0, errors=0, skipped=1, files=117 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |

## Warnings

- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncIntegrationClosureContractTest.java

## Failures

(none)

## Next Steps

- If conclusion is FAIL, fix the listed failures and rerun this script.
- Keep cross-domain mapper whitelist at 0.
- Reduce architecture redline debt by lowering -MaxRedlineDebt over future DDD slices.
