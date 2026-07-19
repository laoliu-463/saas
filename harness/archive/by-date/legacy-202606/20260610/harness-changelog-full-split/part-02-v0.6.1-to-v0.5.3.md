# Harness Changelog 2

Source: ../harness-changelog-full.md (split archive index)

## v0.6.1

- HARNESS-DEBT-GC-001 完成（2026-06-04 00:14）。本轮为 harness 安全清理、归档、瘦身的 docs-only 任务。
- **新增文件**：
  - `harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json`（delete manifest：`nul` + `test-results/` + `playwright-report/`）。
  - `harness/reports/harness-debt-gc-001-inventory-20260604-001052.md`（清理前盘点报告）。
  - `harness/reports/2026-06-12/content-retire-20260604-001401.md`（retire-content.ps1 生成的执行报告）。
  - `harness/reports/2026-06-12/evidence-20260604-001401-harness-debt-gc-001.md`（执行证据）。
  - `harness/reports/retro-20260604-001401-harness-debt-gc-001.md`（复盘）。
- **修改文件**：
  - `harness/state/HARNESS_DEBT.md`：DEBT-013 deferred → **fixed**；DEBT-014 deferred → **wontfix**。
  - `harness/CURRENT_STATE.md`：追加 HARNESS-DEBT-GC-001 完成段。
  - `harness/QUALITY_LEDGER.md`：Harness A- → A（垃圾清理与归档已明确口径）。
- **DEBT 状态**：
  - 关闭：DEBT-013（共 1 条 deferred → fixed）。
  - 重新分类：DEBT-014（共 1 条 deferred → wontfix；76 份 reports 全部被证据链引用，reclassify 为受保护）。
- **删除对象**：
  - `nul`（Windows 设备文件残留 0 bytes）。
  - `test-results/`（playwright 测试结果目录）。
  - `playwright-report/`（playwright HTML 报告 528 KB）。
- **未做**：
  - 未归档任何 reports（全部被证据链引用）。
  - 未修改业务代码 / SQL / Docker / env。
  - 未重启容器 / 未部署远端。
  - 未删除已存在的归档批次（`harness/archive/retired-content/20260603-reports-archive/`）。
  - 未删除任何 evidence / retro 报告。

## v0.6.0

- HARNESS-DEBT-GOVERNANCE-ITERATION 完成（2026-06-03 23:03）。本轮为 harness 自身治理的 docs-only 任务。
- **新增文件**：
  - `harness/state/HARNESS_DEBT.md`（harness 自身工程债务登记；25 条 DEBT）。
  - `harness/runbooks/task-lifecycle.md`（任务生命周期七 Gate 模板：Intake / Scope / Implementation / Verification / Evidence / Git / Exit）。
  - `harness/runbooks/scope-command-matrix.md`（Scope → Command 决策表，取代分散在 TASK_ROUTING 和 doc/02-tools 的描述）。
  - `harness/runbooks/debt-governance.md`（债务防回流机制：Intake / Register / Exit / Dirty / Rotation / Prompt Upgrade）。
  - `harness/environment/CHEATSHEET.md`（环境速查表：本地端口 / 健康检查 URL / env 变量 / 远端信息 / 禁止命令）。
  - `harness/feedback/docs-only-template.md`（docs-only 任务最小化 evidence 模板）。
  - `harness/plans/HARNESS_ITERATION_ROADMAP.md`（Harness 自身迭代路线图；区别于业务 DDD 路线图）。
  - `harness/reports/harness-debt-governance-inventory-20260603-230334.md`（现状盘点）。
  - `harness/reports/harness-debt-governance-plan-20260603-230334.md`（治理计划与债务总表）。
  - `harness/reports/2026-06-12/evidence-20260603-230334-harness-debt-governance.md`（执行证据）。
  - `harness/reports/retro-20260603-230334-harness-debt-governance.md`（本次复盘）。
- **修改文件**：
  - `harness/CURRENT_STATE.md`：顶部加目录指针。
  - `harness/QUALITY_LEDGER.md`：加与 DOMAIN_STATUS 分工指针；Harness 等级 B → A-。
  - `harness/README.md`：三目录并存说明（主源 = `harness/`，`harness/doc/` 仅做聚合）。
  - `harness/instructions/safety-rules.md`：主源指针 → `FORBIDDEN_SCOPE.md`。
  - `harness/feedback/garbage-collection-policy.md`：reports/*.md 纳入受保护范围。
  - `harness/state/KNOWN_ISSUES.md` / `p0-p1-register.md` / `known-risks.md`：三文件分工互引。
  - `harness/state/DEPLOYMENT_STATE.md` / `VALIDATION_STATE.md`：主源指针。
  - `harness/state/TASK_HISTORY.md`：补 14 行 2026-06-02 / 2026-06-03 任务摘要。
  - `harness/doc/00-HARNESS-README.md`：主源说明。
- **DEBT 状态**：
  - 关闭：DEBT-001/002/003/004/005/006/007/008/009/010/011/012/015/017/018/019/023/025（共 18 条 in-progress → fixed；DEBT-023/025 是新建登记文件）。
  - 保持 deferred：DEBT-013/014/016/020/021/022/024（共 7 条，明确登记为后续任务）。
- **未做**：
  - 未修改任何业务代码。
  - 未修改 backend / frontend / SQL / Docker / env。
  - 未重启容器 / 未部署远端。
  - 未删除任何报告 / 临时 log / 已存在的 evidence。

## v0.5.7

- 完成 GIT-BATCH-C 业务代码批次提交 + 推送 + 远端部署（2026-06-03 22:50–23:00）。
- 生成报告：`harness/reports/git-batch-c-talent-address-deploy-20260603-225500.md`。
- 提交内容：3 个 commit 全部 push 到 gitee + origin + 远端 deploy。
  - `804f96dc` 上游 `feat(sample): implement talent address default save (TALENT-ADDRESS-SAMPLE-DEFAULT)`（7 文件 / 476 行 +）
  - `16b23416` 上游 `docs(harness): TALENT-ADDRESS-SAMPLE-DEFAULT evidence report and state updates`（4 文件 / 130+ 行）
  - `159fa38d` 本会话 `docs(harness): GIT-INTAKE-001 dirty classify + ORDER-ATTRIBUTION-SAMPLE report`（3 文件 / 660 行 +）
- 推送：gitee + origin 同步推送 `49aefbda..159fa38d`（3 commits ahead）。
- 远端部署：`harness/commands/deploy-remote.ps1` 触发 `compose up -d --build backend-real-pre frontend-real-pre`，远端 backend 容器 Created/Started 2026-06-03T13:57:25.833735461Z，新 jar（Jun 3 13:57 UTC）已加载 `TalentClaimMapper` 注入 + `writeBackClaimAddress` 编译产物。
- 健康检查：远端 backend `{"status":"UP"}` + frontend `ok` + 4 容器全部 healthy。
- 业务验证：字符串验证确认 `ProductQuickSampleService` 已注入 `TalentClaimMapper` + `Lcom/colonel/saas/entity/TalentClaim;` 已链接；完整端到端业务验证在本地 real-pre 已有 evidence（`talent-address-sample-default-20260603-224000.md` H1-H9 PASS）。
- 远端 HEAD 对齐：本地 = gitee/feature/auth-system = origin/feature/auth-system = 远端 `/opt/saas/app` HEAD = `159fa38d`。
- 未清库、未使用 `docker compose down -v`、未执行破坏性 SQL。
- Final Status：`DONE_REMOTE_VERIFIED`。Git Exit Gate：`DONE_CLEAN`（本地工作区 clean + 远端容器 healthy + 远端 commit 对齐）。
- 状态：Batch A/B/C 全部完成。下一步：等 RISK-007 商务侧真实抖店订单样本解锁 / 1-2 小时后 P-VERIFY-002 远端商品库数量复核。

## v0.5.5

- 完成 TALENT-ADDRESS-SAMPLE-DEFAULT 达人寄样地址默认保存（2026-06-03）。
- 生成报告：`harness/reports/talent-address-sample-default-20260603-224000.md`。
- 后端修复：`ProductQuickSampleService` 和 `SampleApplicationService` 添加 `writeBackClaimAddress()` 方法，寄样创建成功后自动回写地址到 `talent_claim`。
- 前端修复：`QuickSampleModal.vue` 添加 `watch(talentIds)` 自动加载默认地址；`SampleCreateModal.vue` 添加 `loadDefaultAddress()` 和地址字段传递。
- 无新增字段或 migration，复用 `talent_claim` 已有 `recipient_name/recipient_phone/recipient_address` 字段。
- 测试结果：后端 1708/0/0、前端 QuickSampleModal 5/5、SampleCreateModal 3/3、typecheck ✅、build ✅。
- real-pre 验收：地址回写 PASS、快照不变 PASS、地址更新 PASS、多渠道隔离 PASS（biz_leader 访问 403）。
- 已部署远端 real-pre（2026-06-03 23:00 SSH deploy），远端健康检查 PASS、容器全 healthy、JAR 新构建。
- Final Status：`PASS`。

## v0.5.6

- 完成 GIT-INTAKE-001 会话启动 dirty 校验与 Batch 重分类（2026-06-03 22:30–22:50）。
- 生成报告：`harness/reports/git-intake-001-dirty-classify-20260603-225000.md`。
- 关键发现：会话启动 hook 报告 HEAD = `ab03d729` 与实际 HEAD = `49aefbda docs(harness): record sample remote verification` 偏差 1 个 commit；启动 hook 报告的 6 个 staged 文档（`harness/CURRENT_STATE.md` / `HARNESS_CHANGELOG.md` / `p0-sample-001-remote-verify-20260603-221004.md` / `retro-20260603-223153.md` / `p0-p1-register.md` / `real-pre-evidence-index.md`）已由上游会话 commit 并推送到 gitee + origin，本会话无需再次 commit/push。
- 远端 HEAD 对齐：本地 = gitee/feature/auth-system = origin/feature/auth-system = `49aefbda`。
- Batch 重分类：
  - Batch A（6 文档）已 clean——`49aefbda` 已包含并推送。
  - Batch B（14 文件，dirty）保持脏状态：6 modified 业务代码（`ProductQuickSampleService` / `SampleApplicationService` / `QuickSampleApplyTest` / `QuickSampleModal.test.ts` / `QuickSampleModal.vue` / `SampleCreateModal.vue`）+ 1 untracked 测试（`SampleCreateModal.test.ts`）+ 3 untracked 报告（`talent-address-sample-default-20260603-224000.md` / `git-intake-001-dirty-classify-20260603-225000.md` / `order-attribution-sample-20260603-222120.md`）+ 3 modified 状态文件（`HARNESS_CHANGELOG.md` / `state/DOMAIN_STATUS.md` / `state/KNOWN_ISSUES.md` / `state/p0-p1-register.md`）。
- 上游会话已先于本会话完成 TALENT-ADDRESS-SAMPLE-DEFAULT 任务（`v0.5.5`），含 6 modified 业务代码 + 1 untracked evidence 报告 + 2 modified 状态文件（`DOMAIN_STATUS.md` / `KNOWN_ISSUES.md`），但未 commit / 未 push / 未部署远端。
- 用户授权："等代码批次一起部署"——Batch B 必须包含 TALENT-ADDRESS-SAMPLE-DEFAULT 任务 + GIT-INTAKE-001 报告 + 订单归因样本报告，一并 commit + 推送 + 远端部署对齐。
- 风险登记：`harness/state/p0-p1-register.md` 追加 RISK-007（订单归因样本不足 BLOCKED_BY_SAMPLE）与 RISK-008（Batch B 14 文件 dirty 未提交）。
- 远端部署：本次未触发（仅 docs 状态收口，无代码变更）。
- 未修改业务代码、未写库、未重启容器、未执行数据库写入 SQL。
- 状态：Batch A `DONE_CLEAN`，Batch B `PARTIAL_DIRTY_REMAINING`（Git Exit Gate：`DONE_WITH_REGISTERED_DIRTY`）。

## v0.5.4

- 完成 P0-SAMPLE-001-REMOTE-VERIFY 远端 real-pre 部署验证状态收口（2026-06-03 22:10-22:30）。
- 生成报告：`harness/reports/p0-sample-001-remote-verify-20260603-221004.md`、`harness/reports/retro-20260603-223153.md`。
- 远端 `/opt/saas/app` 从 `77b723b` 快进到 `ab03d72`（目标 `ab03d729`），远端工作区 clean。
- 部署方式：远端用 Maven Docker 镜像构建后端 jar，仅 rebuild/recreate `backend-real-pre` 与 `frontend-real-pre`；未使用 `-v`，未清库，未重建 PostgreSQL / Redis volume，未执行数据库写入 SQL。
- 健康检查：远端 backend/frontend/postgres/redis 均 healthy；`/api/system/health={"status":"UP"}`，`/healthz=ok`。
- 业务验证：商品库快照 `81a5d39b-e661-3b19-96d3-e55b145f15f1` 快速寄样成功生成 `sample_request=9c655738-76b1-4fd0-9676-8f307c694f3f`，`request_no=QS2026060337AF9AAB`，`status=1 / PENDING_AUDIT / 待审核`；`product` 主表已物化；`crawler_talent_info` 缺失时按 `talent.douyin_uid=56723079343` 兜底。
- 审核列表验证：`admin` 可见，`biz_leader` 可见，`biz_staff` 不可见；失败分支返回 `items[].message` 明细（“该达人未在你的私海中...” / “达人不存在”）。
- 限制：远端 `channel_staff` 无私海达人，唯一有私海达人的等价渠道账号“玄同”无法用已知测试口径登录；远端全部 `product_operation_state.assignee_id` 为空，无法严格证明“该商品分配给 biz_leader”前提。
- 额外风险：`channel_staff` 可查到该待审核单，建议另起 RBAC 专项复核，不在本次远端部署验证中修改。
- Final Status：`PARTIAL`。核心修复链路远端通过；指定账号/分配前置数据不足导致完成标准不能写成全量 PASS。

## v0.5.3

- 完成 ORDER-P0-DUAL-SOURCE-SYNC 本地 real-pre 修复与验证（2026-06-03 19:xx-20:23）。
- 生成报告：`harness/reports/2026-06-12/evidence-20260603-202253.md`、`harness/reports/2026-06-12/evidence-20260603-202320.md`、`harness/reports/retro-20260603-202309.md`、`harness/reports/retro-20260603-202500.md`。
- 修改后端代码：
  - `OrderSyncJob`：新增 `syncInstituteOrdersRecent()` 定时入口，默认每 10 分钟调用 6468。
  - `OrderSyncService`：新增 `syncInstituteOrdersRecentWindow()`，使用独立锁 `ORDER_SYNC_INSTITUTE`、独立 Redis key `order:sync:institute_recent_last_time`、24h 窗口和 `ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel` 日志。
  - `OrderDualTrackAmountResolver` / `OrderSyncPersistenceService`：按来源保护双轨，6468 只写事实/预估轨，2704 保留结算/有效轨，按 `order_id` 幂等合并。
  - `ColonelsettlementOrder` / Mapper：映射 `pay_time`、`order_create_time`，并新增非持久化 `syncSource`。
- 验证：
  - 聚焦测试：37 tests, 0 failures。
  - API/网关/订单测试：43 tests, 0 failures。
  - `mvn -f backend/pom.xml -DskipTests package`：PASS。
  - 本地 backend-real-pre 重启 + health：PASS。
  - real-pre preflight：PASS。
  - 运行态：`ORDER_SYNC_INSTITUTE ... fetched=100 inserted=100 updated=0`；`colonelsettlement_order count(*)=100`；100 条均有 `order_amount/pay_time/estimate_*`，`settle_amount/effective_*` 均未由 6468 写入。
  - 管理员订单列表和未归因列表均返回 `total=100`。
- 未修改 Dashboard summary；另开 `ANALYTICS-DUAL-TRACK-SUMMARY-FIX`。
- 未部署远端；远端验证待用户明确授权。
- 本轮上游样本 `pick_source=0`，渠道 mapping 命中正向验证仍为 PENDING。
- Final Status：`PARTIAL_DIRTY_REMAINING`——本地 real-pre 订单入库和管理员可见通过；远端部署/验证未执行；混合 dirty 工作区下未使用 `git add -A`。

