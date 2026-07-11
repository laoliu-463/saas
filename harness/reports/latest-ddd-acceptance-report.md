# DDD Acceptance Latest Report

## Metadata

- Time: 2026-07-11 15:09:25 +08:00
- Branch: codex/ddd-sample-query-completion
- HEAD: 4da17da8
- Conclusion: PASS

## Dirty Files

- Total: 1
- docs/harness: 1
- known historical tests: 0
- unexpected non docs/harness: 0

~~~text
 M harness/reports/latest-ddd-acceptance-report.md
~~~

## Whitelist

| File | Active | Total Lines |
| --- | ---: | ---: |
| cross-domain-mapper-legacy-whitelist.txt | 0 | 10 |
| architecture-redline-legacy-whitelist.txt | 0 | 3 |

## Matrix

| DONE | PARTIAL | TODO | BLOCKED | Total |
| ---: | ---: | ---: | ---: | ---: |
| 133 | 37 | 0 | 8 | 178 |

## Checks

| Check | Status | Summary | Command |
| --- | --- | --- | --- |
| git status | WARN | dirtyFiles=1 | git status --short |
| cross-domain mapper whitelist | PASS | active=0, totalLines=10 |  |
| architecture redline whitelist | PASS | active=0, totalLines=3 |  |
| DDD evidence matrix | PASS | DONE=133, PARTIAL=37, TODO=0, BLOCKED=8, total=178 |  |
| git diff --check | PASS | exitCode=0 | git diff --check |
| check-harness-limits | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 |
| safety-check docs dry-run | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun |
| mvn compile | PASS | exitCode=0 | cd backend; mvn -DskipTests compile |
| DddArchitectureRedlineGuardTest | PASS | exitCode=0 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| DddArchitectureRedlineGuardTest surefire | PASS | tests=4, failures=0, errors=0, skipped=0, files=1 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| wide DDD architecture tests | PASS | exitCode=0 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |
| wide DDD architecture tests surefire | PASS | tests=258, failures=0, errors=0, skipped=1, files=85 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |

## Warnings

(none)

## Failures

(none)

## Next Steps

- If conclusion is FAIL, fix the listed failures and rerun this script.
- Keep cross-domain mapper whitelist at 0.
- Reduce architecture redline debt by lowering -MaxRedlineDebt over future DDD slices.
