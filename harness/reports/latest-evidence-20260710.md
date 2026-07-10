# Evidence Report 2026-07-10

## 结论
PARTIAL

本日先完成 T-9/T-10/T-15 本地 evidence 与 `GIT-INTAKE / LEDGER-RECONCILE-001`，随后完成 Y-4 乐观锁冲突修复。路径级 dirty 仍按既有 intake 保护；Y-4 恢复后 canonical 矩阵为 DONE=133 / PARTIAL=37 / TODO=0 / BLOCKED=8。real-pre 仍因抖音 token 与真实样本不足，不能声明整体业务闭环 PASS。

## 证据

### 2026-07-10 22:21 迁移批次更新

- 商品库分页已改为 `ProductController → ProductLibraryPageQueryService → ProductLibraryApplicationService → ProductLibraryQueryPort`；Legacy `ProductService` 依赖收口在 `LegacyProductLibraryQueryAdapter`，游标结果改用应用层 `ProductLibraryCursorPage`。
- 寄样详情已改为 `SampleQueryApplicationService → SampleDetailQueryPort`；当前实现仍由 `LegacySampleDetailQueryAdapter` 委托旧查询服务，列表、看板、导出和命令路径未混入本批。
- 商品域回归：352 tests / 0 failures / 0 errors / 0 skipped；宽口径 DDD/架构/合同：368 tests / 0 failures / 0 errors / 1 skipped。
- 本批提交：`c2e805f0 refactor: route product and sample reads through ddd ports`；推送因 GitHub 443 网络不可达失败，未写成已推送。
- `agent-do -Env real-pre -Scope full`：backend package PASS、frontend build PASS、Docker 重建/重启 PASS、backend/frontend health PASS；business preflight `BLOCKED_AUTH`，未执行真实上游业务流。
- 原始报告已归档：`harness/archive/by-date/20260710/reports-limit-cleanup/222300/evidence-20260710-222112.zip`；完整 preflight：`runtime/qa/out/real-pre-preflight-20260710-222110/report.md`。

- 环境：real-pre
- 分支：`codex/ddd-user-role-application`
- 验证代码 HEAD：`3ed74608`
- 本轮卡片：T-9、T-10、T-15、Y-4
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

## Y-4 Optimistic Lock Conflict

- 时间：2026-07-10 13:17-13:45 +08:00；环境：本地 `real-pre`；远端部署：否。
- 根因：`CommissionRuleService.update/delete` 丢弃 `updateById` 影响行数，MyBatis-Plus 乐观锁更新 0 行时仍返回成功。
- RED-1：update/delete 两个冲突测试均失败，原因均为“Expecting code to raise a throwable”；2 tests / 2 failures。
- GREEN-1：接入 `OptimisticLockSupport.requireUpdated` 后，`CommissionRuleServiceTest` 21 tests 与 `OptimisticLockSupportTest` 2 tests 全部通过。
- RED-2：创建请求传入 `version=99` 时，测试得到 expected 1 / actual 99。
- GREEN-2：创建版本改为服务端无条件写 1；创建行为和版本合同测试通过。
- 最终测试：Y-4 合同、配置路由、Service、乐观锁与 redline 共 47 tests / 0 failures / 0 errors / 0 skipped。
- 构建：`mvn -f backend/pom.xml -DskipTests package` BUILD SUCCESS；仅有既有 Spring Boot plugin 参数与 JaCoCo execution-data mismatch warning。
- 数据库：迁移前 `\d commissions` 确认缺少 `version`；容器内 schema-only 备份 2780 bytes；事务回滚演练成功；只执行单条幂等 `ADD COLUMN IF NOT EXISTS`，未重跑含历史 DML 的 `migrate-all.sql`。迁移后为 `version integer NOT NULL DEFAULT 1`，当时表 0 行、null_versions=0。
- 运行态：backend 镜像 `sha256:b5ecd8d12bdd6509b48b4b90c7aa1ccaf048c2cc436f465527dda05ee8cd37d3`，容器 `running|healthy`；health 返回 `200 {"status":"UP"}`。
- 业务 smoke：管理员登录 HTTP/业务码 200；`GET /api/commission-rules?page=1&size=1` HTTP/业务码 200，total=0。
- 两轴复核：原“提交不自包含”和“客户端污染初始版本”问题均关闭；限定范围未发现新 P0-P2。code-review-graph 在隔离 worktree 为空、主仓库查询超时；提交钩子随后成功增量更新图谱。
- 收尾门禁：canonical docs-only acceptance PASS，matrix DONE=133 / PARTIAL=37 / TODO=0 / BLOCKED=8；88 条 warning 为已登记 dirty 路径提示；docs safety-check PASS；`git diff --check` PASS（仅 CRLF/LF warning）；Harness 50/50/200 PASS。
- 提交：`4bb8ce1c fix(performance): add commission rule version migration`；`3ed74608 fix(performance): enforce commission rule optimistic lock`。
- 结论：Y-4 `DONE`；不代表远端部署或真实订单提成 E2E 通过。

## 风险
- real-pre preflight：`BLOCKED_AUTH`，`hasAccessToken=false`、`hasRefreshToken=true`、`reauthorizeRequired=false`。
- 本轮只证明本地达人地址、标签、跟进审计和寄样地址联动合同，不证明真实浏览器取址、外部抖店寄样或真实样本闭环。
- Y-4 SQL 与 Java/测试已提交，状态/证据文档待本轮提交；工作区其余大量历史 dirty 继续按 Git Intake 的 previous-partial/用户工作保护，未混入 Y-4 批次。
- 提成规则前端请求未透传原始版本；当前修复能检测 Service 读写窗口内的并发更新，但不能证明跨页面陈旧提交冲突，已登记独立 open issue。
- `agent-do.ps1` 当前会跨批次调用自动暂存/推送脚本；在 dirty 分批收口前不能安全直接使用非 DryRun。
- GitHub Issues 远端只读核对因 API 网络超时未完成，本地镜像仍是 2026-06-20 快照。

## Retro Summary

- 本轮无需 Harness 能力升级；现有 TDD、数据库变更、Compose 重启、健康检查与 Git 分批门禁足以完成 Y-4。
- 经验：合同测试会读取未暂存 SQL，若先提交 Java 会产生“工作树通过、干净检出失败”；因此调整为 SQL 父提交 + Java/测试子提交。
- 经验：版本字段必须由服务端初始化，不能信任实体请求体中的客户端值。

## 下一步
- 独立修复 E-7 转链事件幂等键透传；不得与 Y-4 或其他事件卡混合提交。
- 真实链路卡需先解决抖音 token 或提供可复跑样本。
