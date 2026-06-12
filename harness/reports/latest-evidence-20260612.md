# Evidence Report - 1603 结算口径切换

## Metadata

- Time: 2026-06-12 12:34 +08:00
- Environment: local real-pre
- Branch: feature/ddd/DDD-SAMPLE-005-FIX-sample-agent
- Commit: f3846415
- Worktree: dirty
- Git commit/push: not performed due to broad pre-existing dirty worktree
- Remote deploy: false
- Conclusion: PARTIAL

## Scope

- 将订单结算主读取口径切换为 `buyin.instituteOrderColonel`。
- 将 `buyin.colonelMultiSettlementOrders` 降级为 fallback / probe / contrast。
- 不修改归因规则、前端业务规则、业绩公式。
- 不做 pay -> settle 或 estimate -> effective 的硬兜底。

## Build And Test

- PASS: `mvn '-Dtest=*1603*,*Settlement*,*PerformanceCalculationEffectiveTrack*' test`
  - 11 tests, 0 failures, 0 errors.
- FAIL: `mvn test`
  - Full suite compiled and ran, but existing non-1603 tests failed.
  - Examples: `SysConfigServiceEventTest`, `SysConfigServiceTest`, `SysDeptServiceTest`, talent provider/schema tests, `ProductDisplayRuleServiceTest`.
  - One repeated failure source: Mockito inline mock cannot redefine `ProductBizStatusService` on current JVM.
- PASS: `mvn -Dmaven.test.skip=true package`
- PASS: `npm test -- --run src/api/douyin.test.ts`
  - 39 tests, 0 failures.
- PASS: `npm run build`
- PASS: Harness safety check.
- PASS: Harness backend build.
- PASS: Harness frontend build.

## Runtime Verification

- FAIL/BLOCKED: Harness Docker restart.
- Blocking evidence: Docker Desktop Linux engine pipe missing:
  `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`.
- NOT COLLECTED: Docker status.
- NOT COLLECTED: health check.
- NOT COLLECTED: real-pre 1603 raw probe.
- NOT COLLECTED: real-pre 1603 dry-run endpoint call.
- NOT COLLECTED: small time-window write verification.

## Evidence Artifacts

- Archived raw Harness reports: `harness/archive/evidence-raw-20260612.zip`
- Interface audit: `harness/reports/order-1603-settlement-interface-audit.md`
- Harness limits: `harness/reports/latest-harness-limits-check.md`

## Residual Risk

- 当前只能证明代码路径、映射策略和构建/目标测试通过，不能证明真实抖音 1603 上游字段完整。
- Docker 未启动导致容器重启、健康检查和业务联调未完成。
- real-pre 小窗口写入验证必须在 Docker engine 可用后补跑。
- npm audit 报告 2 个 critical 依赖风险，本轮未处理。

## Retro

- 本次无需 Harness 规则升级。
- 运行阻塞来自本机 Docker engine 不可用，不是 Harness 流程缺项。
