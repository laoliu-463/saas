# Evidence Report 2026-07-10

## 结论
PARTIAL

本日先完成 T-9/T-10/T-15 本地 evidence，随后执行 `GIT-INTAKE / LEDGER-RECONCILE-001`。路径级 dirty 已全部分类，矩阵纠偏为 DONE=132 / PARTIAL=38 / BLOCKED=8；real-pre 仍因抖音 token 与真实样本不足，不能声明业务闭环 PASS。

## 证据
- 环境：real-pre
- 分支：`codex/ddd-user-role-application`
- HEAD：`6598d623`
- 本轮卡片：T-9、T-10、T-15
- 修改文件：
  - `backend/src/test/java/com/colonel/saas/architecture/DddTalentDomainInventoryEvidenceTest.java`
  - `docs/ddd-completion-evidence-matrix.md`
  - `harness/rules/state/snapshots/DOMAIN_STATUS.md`
  - `harness/reports/latest-ddd-acceptance-report.md`
- 原始 agent-do 报告：`harness/archive/by-date/2026-07-10/reports-limit-cleanup/evidence-20260710-120037.zip`

## 命令
- `mvn -q test "-Dtest=DddTalentDomainInventoryEvidenceTest,DddTalentSampleFacadeBoundaryTest,TalentProfileApplicationServiceTest,TalentControllerTest,TalentFollowServiceTest,QuickSampleApplyTest"`：30 tests / 0 failures / 0 errors / 0 skipped
- `mvn -q test "-Dtest=*Talent*Test,*Sample*Test"`：553 tests / 0 failures / 0 errors / 0 skipped
- `mvn -q test "-Dtest=DddTalentDomainInventoryEvidenceTest,DddTalentDomainStatusEvidenceIndexTest"`：PASS
- `mvn -q -DskipTests compile`：PASS
- `mvn -q test "-Dtest=DddArchitectureRedlineGuardTest"`：PASS
- `mvn -q test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"`：363 tests / 0 failures / 0 errors / 1 skipped
- `git diff --check`：PASS，仅 CRLF/LF warning
- 早期 `check-ddd-acceptance.ps1 -RequireRedlineZero`：PASS，matrix DONE=134 / PARTIAL=36 / TODO=0 / BLOCKED=8；该计数已被后续两轴审查纠偏
- `agent-do.ps1 -Env real-pre -Scope backend`：backend package PASS，Docker restart PASS，health `200 {"status":"UP"}`，business validation BLOCKED

## Ledger Reconcile

- Git leaf dirty：137（74 modified / 8 deleted / 55 untracked），staged empty，path-level unknown=0。
- 分类报告：`harness/reports/git-intake-20260710-125023.md`。
- Y-4 因乐观锁更新结果未校验、E-7 因转链事件未透传幂等键，从 DONE 暂降 PARTIAL。
- `check-ddd-acceptance.ps1 -DocsOnly -RequireRedlineZero`：PASS；matrix DONE=132 / PARTIAL=38 / TODO=0 / BLOCKED=8。
- docs safety-check：PASS。
- `git diff --check`：PASS，仅 CRLF/LF warning。
- `check-harness-limits.ps1`：先复现 321 行原始报告违规；受控归档打包后 PASS。
- 清理 manifest：`harness/manifests/reports-cleanup-20260710.json`。
- 清理报告：`harness/reports/content-retire-20260710-125326.md`。

## 风险
- real-pre preflight：`BLOCKED_AUTH`，`hasAccessToken=false`、`hasRefreshToken=true`、`reauthorizeRequired=false`。
- 本轮只证明本地达人地址、标签、跟进审计和寄样地址联动合同，不证明真实浏览器取址、外部抖店寄样或真实样本闭环。
- 工作区仍有大量历史 dirty，未提交、未 push。
- `agent-do.ps1` 当前会跨批次调用自动暂存/推送脚本；在 dirty 分批收口前不能安全直接使用非 DryRun。
- GitHub Issues 远端只读核对因 API 网络超时未完成，本地镜像仍是 2026-06-20 快照。

## 下一步
- 先以行为测试修复 Y-4 乐观锁冲突，再独立修复 E-7 幂等键透传；两卡不得混合提交。
- 真实链路卡需先解决抖音 token 或提供可复跑样本。
