# Evidence Report - 2026-07-08

## Metadata

- Time: 2026-07-08 18:45 +08:00
- Environment: local real-pre repository context
- Branch: codex/ddd-user-role-application
- HEAD: 6598d623
- Scope: DDD Y-5 refund reversal and summary exclusion evidence
- Selected Gate: Gate 3 - Domain Change
- Remote deploy: not requested, not executed
- Commit / push: not executed
- Worktree: dirty. Existing backend/docs/harness dirty files remain; this turn updated Y-5 matrix evidence, DOMAIN_STATUS and latest reports only.

## Selected Cards

- Matrix source: `docs/ddd-completion-evidence-matrix.md`
- Selected card: Y-5.
- Status after verification: DONE.
- Matrix count after update: DONE 83 / PARTIAL 67 / TODO 22 / BLOCKED 6.
- Reason: Y-5 now has current card-level evidence for refund reversal and summary exclusion after the wide architecture gate is green.

## Boundary Check

- 主责领域：订单域发布退款事实。
- 关联领域：业绩域消费订单退款事实并执行已有冲正计算；分析模块只做 shadow 路由。
- 允许调用：订单事件 publisher/outbox、订单 read facade、业绩应用服务、分析事件消费者。
- 禁止调用：订单域计算提成、写 `performance_records`、直接改寄样状态、应用独家覆盖。
- 发现的越界风险：未发现本轮新增越界；退款金额和退款 ID 只从上游 `extraData` 显式字段透传，未知金额不从订单金额推导。
- 阶段性结论：本轮证明本地退款事实事件合同和消费者闭环，不证明真实上游或 E2E。

## Evidence

- Y-5 DONE: `OrderCommissionPolicy` excludes cancelled/refunded statuses from performance.
- `PerformanceCalculationApplicationService.upsertFromOrder` keeps the same performance record id, advances calculation version, writes `valid=false` / `reversed=true`, and zeroes service profit, recruiter/channel commission and gross profit for refunded/invalidated orders.
- `PerformanceSummaryApplicationService` filters `co.order_status NOT IN (4, 5)`, `COALESCE(pr.is_valid, true)=true`, and `COALESCE(pr.is_reversed, false)=false`.
- `DashboardPerformanceSummaryService.applyOrderSynced` skips refunded/cancelled orders through `OrderCommissionPolicy.countsTowardPerformance`.
- `PerformanceBackfillService` still delegates stale invalidated orders to performance calculation through `OrderReadFacade.findInvalidatedOrdersWithStalePerformance`.

## Commands

| Command | Result | Evidence |
| --- | --- | --- |
| code-review-graph `get_review_context` for O-13 files | REVIEWED | medium risk context, impacted order/performance/analytics nodes checked |
| code-review-graph `detect_changes` | REVIEWED | risk 0.80 due broad dirty worktree; O-13 changed files inspected before verification |
| `mvn -q -DforkCount=0 test "-Dtest=DddOrderRefundReversalContractTest,OrderEventPayloadMapperTest,DddOutbox001OrderRoutingTest,OrderSyncPersistenceServiceTest,PerformanceRecordSyncListenerTest,AnalyticsEventConsumerTest,AnalyticsShadowEventListenerTest,PerformanceCalculationApplicationServiceTest,PerformanceCalculationServiceTest,PerformanceBackfillServiceTest,DddPerformanceCalculatedEventContractTest"` | PASS | target evidence set passed; expected negative-branch logs from listener/calculation tests |
| `mvn -q -DskipTests compile` | PASS | exitCode=0 |
| `mvn -q -DforkCount=0 test "-Dtest=DddArchitectureRedlineGuardTest"` | PASS | redline guard PASS |
| `mvn -q -DforkCount=0 test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"` | PASS | wide architecture/contract tests PASS |
| `mvn -q -DforkCount=0 test "-Dtest=*Order*Test,*Performance*Test,*Analytics*Test"` | PASS | surefire 184 classes, 830 tests / 0 failures / 0 errors / 1 skipped |
| `mvn -q -DforkCount=0 test "-Dtest=DddOrderRefundReversalContractTest,OrderEventPayloadMapperTest,DddOutbox001OrderRoutingTest,OrderSyncPersistenceServiceTest,PerformanceRecordSyncListenerTest,PerformanceCalculationApplicationServiceTest,PerformanceCalculationServiceTest,PerformanceBackfillServiceTest,AnalyticsEventConsumerTest,AnalyticsShadowEventListenerTest"` | PASS | O-10 target evidence set: 67 tests / 0 failures / 0 errors / 0 skipped; expected negative-branch logs from listener tests |
| `mvn -q -DforkCount=0 test "-Dtest=DddPerformanceRefundReversalSummaryContractTest,PerformanceCalculationApplicationServiceTest,PerformanceSummaryApplicationServiceTest,DashboardPerformanceSummaryServiceTest,PerformanceBackfillServiceTest,PerformanceRecordSyncListenerTest,PerformanceSummaryServiceTest"` | PASS | Y-5 target evidence set: 36 tests / 0 failures / 0 errors / 0 skipped; expected negative-branch logs from listener tests |
| `mvn -f backend/pom.xml -DskipTests package` | PASS | backend jar repackaged; BUILD SUCCESS at 2026-07-08T18:24:03+08:00 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend` | PASS | backend image rebuilt; `backend-real-pre` container recreated and started |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope backend` | PASS | backend health `http://127.0.0.1:8081/api/system/health` returned 200 and `{"status":"UP"}` |
| `npm run e2e:real-pre:p0:preflight` | BLOCKED_AUTH | report `runtime/qa/out/real-pre-preflight-20260708-182526/report.md`; status BLOCKED because Douyin token readiness hasAccessToken=false, hasRefreshToken=true, reauthorizeRequired=true |

## Acceptance Validation

| Command | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PASS | exitCode=0; CRLF/LF warning only for `latest-ddd-acceptance-report.md` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1` | PASS | harness 50/50/200 check PASS |
| `.\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt 6` | PASS | latest report regenerated; redline active=0, matrix 81/69/22/6 |
| `.\harness\scripts\check-ddd-acceptance.ps1 -RequireRedlineZero` | PASS | current redline active=0, so this is positive PASS on 2026-07-08 |
| `.\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt 5` | PASS | current redline active=0 satisfies tightened threshold |
| `.\harness\scripts\check-ddd-acceptance.ps1 -DocsOnly` | PASS | Maven skipped; whitelist, matrix, diff, harness limits and docs safety checked |
| `.\harness\scripts\check-ddd-acceptance.ps1 -FailOnUnexpectedDirty -OutputPath $env:TEMP\ddd-acceptance-dirty-20260708-o13.md` | EXPECTED FAIL | report conclusion FAIL; listed unexpected non docs/harness dirty files |
| `.\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt -1 -SkipMaven -OutputPath $env:TEMP\ddd-acceptance-redline-negative-20260708-o13.md` | EXPECTED FAIL | report conclusion FAIL; `architecture redline whitelist exceeds -MaxRedlineDebt -1, actual=0` |
| final `.\harness\scripts\check-ddd-acceptance.ps1 -RequireRedlineZero` | PASS | `harness/reports/latest-ddd-acceptance-report.md` refreshed at 2026-07-08 18:21:49 +08:00; compile PASS, redline guard 4 tests PASS, wide DDD architecture 314 tests / 0 failures / 0 errors / 1 skipped |

## Reports

- Matrix: `docs/ddd-completion-evidence-matrix.md`
- Domain status: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Acceptance: `harness/reports/latest-ddd-acceptance-report.md`
- Evidence: `harness/reports/latest-evidence-20260708.md`

## Conclusion

Y-5 is DONE at local code/test/contract level. Refund reversal and summary exclusion are now covered by a card-specific evidence row and a 36-test target set.

Runtime validation remains PARTIAL based on today's latest runtime evidence: backend package, backend container rebuild/restart and backend health passed earlier on 2026-07-08, but this Y-5 evidence-only slice did not add production code or rerun containers. Full real-pre business flow remains blocked by Douyin authorization (`BLOCKED_AUTH`), and no real refund sample, SQL/API reconciliation, frontend page check or E2E business flow passed in this slice.

## Retro

No harness upgrade was required. The next useful slice is O-9 order list data-scope account-difference evidence, O-10 refund fact broader evidence, or O-15 when real upstream samples become available.
