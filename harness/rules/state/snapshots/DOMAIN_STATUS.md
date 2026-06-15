# Domain Status

## 作用

本文件记录 DDD 领域优化状态。它不替代 `docs/领域/*.md` 的领域合同，也不代表代码已经完成对应重构。

## 任务结束状态更新规则

每次任务结束前，Agent 必须更新本文件中与本次任务相关的领域状态。更新内容包括：

1. 当前状态摘要（已完成到哪一步）。
2. 报告路径（evidence report 路径）。
3. 当前风险（新发现或已变化）。
4. DDD 优化下一步（下一阶段任务标识）。
5. 标记优先级（P0 / P1 / P2）。

如果本次任务未涉及某个领域，该领域状态保持不变，不得无故修改。

### Session Exit 时的领域状态更新

每次会话退出前，Agent 必须确保：

1. 本次涉及的领域状态已更新到最新。
2. 每个领域条目的"DDD 优化下一步"指向具体任务标识。
3. 新发现的风险已写入"当前风险"。
4. 报告路径已追加到对应领域。
5. 如果模块质量发生变化，同步更新 `harness/QUALITY_LEDGER.md`。

## 状态口径

- `P0`：影响 V1 主链路、权限、归因、业绩、寄样或 real-pre 验收。
- `P1`：影响领域边界、回归风险或主要运维效率。
- `P2`：治理、清理、补文档或体验优化。

## 用户域

- 当前状态：U-1 现状盘点、U-2 表结构与领域模型对齐、U-2.5-A dept_type 统一方案设计、U-2.5-B dept_type 最小修复已完成；TEST-1 已修复前序全量后端测试失败（2026-06-03）。
- 报告路径：U-1 `harness/reports/user-domain-u1-inventory-20260603-120000.md`；U-2 `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`；U-2.5-A `harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`；U-2.5-B `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`；TEST-1 `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`。
- U-2.5-B 处理结果：`DeptType.java` 作为唯一 Java 标准，标准值为 `department/recruiter_group/channel_group/ops_group`；`DeptTypes.java` 已删除；`service.SysDeptService` 已迁移到 `DeptType.java`；seed/init 和既有 dept_type 幂等脚本不再写入 `recruiter/channel/dept` 作为新标准。
- 当前风险：12 处跨域 Mapper 尚未处理；`PerformanceAccessScope` 尚未处理；`DataScopeResolver` 尚未统一；`UserDomainFacade` 尚未抽象；`sys_role_menu` FK CASCADE 尚未处理；real-pre 历史 `sys_dept.dept_type` 当前只读对账仍为 `department=3`，如需修复必须单独 DB 任务执行；U-2.5-B 新 jar 尚未通过容器重启加载验证。
- 待优化能力：CurrentUser record、PermissionContext 多角色并集、DataScopeResolver 统一、PermissionChecker 统一、UserDomainFacade、越权负例补齐。
- DDD 优化下一步：U-2.5-D 安全拆分提交与状态收口；之后进入 U-3 CurrentUser / PermissionContext 统一。
- 标记：P0。

## 配置域

- 当前状态：配置读取、变更和审计主链路已具备。
- 已完成能力：配置 API、规则参数、配置变更日志。
- 待优化能力：配置域只出参数的边界审查、配置消费方梳理、异常分支和审计证据补齐。
- DDD 优化下一步：C-1 盘点配置域代码、接口、表和测试。
- 标记：P1。

## 订单域

- 当前状态：订单事实、退款事实、同步日志和归因输入已具备；P0-ORDER-001 PAY_RECENT 6h 补拉与同步日志增强已完成（2026-06-03，代码 + 运行态）；ORDER-P0-DUAL-SOURCE-SYNC 已在本地 real-pre 接入 1603 事实订单源并验证入库；ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY 已完成远端部署验证，远端 commit 对齐 `77b723b6`，1603 入库与管理员可见通过；订单明细表字段对齐已完成本地 real-pre 验证（2026-06-04，commit `abf3f9eb`）；ORDER-DETAIL-TAB-FIX-001 已完成前端 16 列扩展与“渠道”文案统一（2026-06-05，commit `db934d99`）；ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 已完成本地 real-pre 修复验证（2026-06-06），订单已同步事件改为事务提交后发布。
- 已完成能力：订单同步（默认 1603 `INSTITUTE_SETTLEMENT` 结算口径 + PAY_RECENT 6h 30min 兜底回扫 + 1603 INSTITUTE_RECENT 24h 事实订单源）、订单入库、退款事实、归因输入保存、双轨独立 Redis 水位 + 独立锁、同步日志含 `api/mode/timeType/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed` 维度。
- P0-ORDER-001 报告路径：`harness/reports/p0-order-001-real-order-visible-20260603-180450.md`、`harness/reports/p0-order-001-diagnosis-20260603-173500.md`、`harness/reports/p0-order-001-intake-20260603-172923.md`。
- P0-ORDER-001 修改文件：`backend/src/main/java/com/colonel/saas/job/JobLockKeys.java`、`OrderSyncJob.java`、`backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`、`backend/src/main/resources/application.yml`，新增/增强测试 3 个文件 13 用例。
- P0-ORDER-001 运行态对账：本地 backend-real-pre 重启后 health=UP，scheduler-2 立即执行 INCREMENTAL（窗口 628s ≈ 10min+overlap），scheduler-4 立即执行 PAY_RECENT（窗口 21600s = 6h），两 Redis key `order:sync:last_time` + `order:sync:pay_recent_last_time` 共存独立。
- ORDER-P0-DUAL-SOURCE-SYNC 本地证据：`harness/reports/evidence-20260603-202253.md`；1603 事实同步日志 `fetched=100 inserted=100`，订单表 `count(*)=100`，100 条均有 `order_amount/pay_time/estimate_*`，`settle_amount/effective_*` 为 0 条有值，管理员订单列表和未归因列表均返回 `total=100`。
- ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY 远端证据：`harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md`；远端 `backend-real-pre` health=UP，1603 事实同步日志 `fetched=100 inserted=100 updated=0`，2704 对照日志 `fetched=0`，远端订单表 `count(*)=100`，管理员 `/api/orders total=100`、`/api/orders/unattributed total=100`。
- ORDER-DETAIL-FIELD-ALIGN 本地 real-pre 证据：`harness/reports/evidence-20260604-191102.md`；页面 smoke `runtime/qa/out/order-detail-page-smoke-20260604-191711/result.json`。`/api/data/orders/detail` 200、total=738、records=20；订单明细页面 16 个目标列可见，商品信息可见，未结算样本 `6953418877360936032` 显示 `结算：-`，页面不含“媒介”和“毛利”。
- ORDER-DETAIL-TAB-FIX-001 前端展示证据：`harness/reports/order-detail-tab-fix-001-20260605-100305.md`；`harness/reports/evidence-20260604-221838.md`；页面 smoke `runtime/qa/out/order-detail-tab-fix-001-20260605-020048/result.json`。`/api/data/orders/detail` 200、total=933、records=20；订单明细页面 16 个最终表头可见，页面不含“媒介”和“毛利”，金额列显示人民币，未结算 null 显示 `-`。
- ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 证据：`harness/reports/order-performance-event-after-commit-fix-001-20260606-121000.md`；Harness evidence `harness/reports/evidence-20260606-120829.md`；real-pre preflight `runtime/qa/out/real-pre-preflight-20260606-120648/report.md`。本地后端全量测试 1730/0/0，`backend-real-pre` rebuild/recreate 后 healthy；SQL anti-join 显示历史缺口仍为 `missing_performance=15`。
- 待优化能力：`pick_source` 返回样本与 mapping 命中后的渠道正向可见性验证；Dashboard summary 双轨聚合另开 `ANALYTICS-DUAL-TRACK-SUMMARY-FIX`；如需远端同步展示订单明细新表头，另起远端部署任务。
- 当前风险：真实订单 PAY_RECENT 周期最坏 30 分钟可见延迟，如商务要求更短可调小 cron；抖音 update_time 上限延迟超 6h 时 PAY_RECENT 也会丢，6h 为当前观测经验值；1603 是否稳定返回 `settle_time` / `effective_*` / `flow_point` 仍需 raw probe + dry-run 取证；渠道可见性正向样本仍 PENDING；订单事件时序根因已修复本地 real-pre，但历史 `performance_records` 缺口仍需 backfill，当前本地 anti-join 为 15。
- DDD 优化下一步：先执行 `ORDER-PERFORMANCE-BACKFILL-001` 清理历史缺口；有 `pick_source` 样本后做渠道可见性验证；之后进入 P0-SAMPLE-001 寄样流转，Dashboard summary 双轨单独处理；远端订单明细展示按部署需求单独执行。
- 标记：P0。

## 业绩域

- 当前状态：最终归属、提成、冲正和汇总主链路已具备；订单明细 BFF 已按 orderId 批量读取 `performance_records` 补全招商提成、渠道提成、服务费支出和服务费收益展示字段；ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 已验证新订单事件在提交后发布，业绩 Listener 正常走 `upsertFromOrder`，重复事件仍走 upsert 幂等路径；SERVICE-FEE-INCOME-FORMULA-CODE-001 已按 2026-06-06 用户口径补齐服务费收入公式解析、结算轨不重复扣技术服务费和后端单元验证。
- 已完成能力：`performance_records`、最终归属、提成、冲正、汇总刷新。
- 待优化能力：输入追溯、重复消费幂等、冲正证据、权限数据范围和 dashboard 对账补齐；继续修复 DASH-MONEY-P0-001 结算轨污染问题，避免业绩表历史数据影响看板结算口径；执行 `ORDER-PERFORMANCE-BACKFILL-001` 将当前本地 15 条历史缺口补齐并重新 anti-join；补充 real-pre API / SQL / 页面级服务费收入与收益双轨验收。
- DDD 优化下一步：先执行 `ORDER-PERFORMANCE-BACKFILL-001`；之后进入 Y-1 盘点业绩域代码、接口、表、任务和测试。
- 标记：P0。

## 分析模块

- 当前状态：dashboard、报表和只读汇总主链路已具备；数据平台订单页已保留汇总模块，并新增/收口订单明细 Tab 与 16 列订单级明细导出；ORDER-DETAIL-TAB-FIX-001 已补齐订单明细 Tab 前端 16 列展示、人民币金额格式与“渠道”文案统一；SERVICE-FEE-INCOME-FORMULA-CODE-001 已更新经营指标矩阵服务费收入 / 收益双轨公式口径与后端展示层单测。
- 已完成能力：看板汇总、报表查询、导出能力。
- 待优化能力：只读边界审查、dashboard API/SQL 对账、admin/group/self 差异验证；Dashboard summary 双轨聚合和历史结算轨污染仍需专项处理；补充 dashboard / 前端页面级服务费收入与收益双轨验收。
- DDD 优化下一步：进入 A-1 盘点分析模块代码、接口、表和测试。
- 标记：P0。

## 商品域

- 当前状态：商品库、活动商品同步、转链和映射主链路已具备。FUNC-001 卡片改造已完成。P-FIX-001C 分页弱化已完成。P-DIAG-002 商品库数量不足排查已完成。P-FIX-002A 同步任务 5 分钟周期配置已完成。P-FIX-002 代码与配置准备已完成。P-FIX-002D 本地运行态验证已完成。P-FIX-002D-REMOTE 远端部署验证已完成（2026-06-03），远端 commit=dea06e4c，同步参数生效，两个周期零冲突。GIT-BATCH-2 frontend-product-ui 已于 2026-06-03 14:08 提交并部署（commit=5fe6ba23）。PRODUCT-LIBRARY-FULL-BACKFILL-FIX-001 已完成代码修复并上线 real-pre：dry-run 安全门禁、表结构、构建健康和商品库 DISPLAYING 展示口径保护均通过；真实历史商品补数未执行，待显式 confirm 后分阶段执行。
- 已完成能力：商品库、活动商品、转链、`pick_source_mapping`；FUNC-001 卡片 UI；P-FIX-001C 分页优化；P-FIX-002A 同步周期配置；P-FIX-002B 唯一索引冲突修复（两遍处理）。
- P-FIX-002 修复结论：`applyNormalDisplayDedup` 改为三阶段持久化（先降级旧 DISPLAYING→HIDDEN，再处理其他非 DISPLAYING，最后升级新 winner→DISPLAYING），避免 `uk_pos_one_displaying_per_product` partial unique index 冲突。新增/补齐 4 个相关测试覆盖严格调用顺序、切换顺序、幂等性和多候选场景。
- P-FIX-002 报告路径：`harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`。
- P-FIX-002D 报告路径：`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`。
- P-FIX-002D-REMOTE 报告路径：`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md`。
- P-FIX-002 修改文件：`ProductActivitySyncJob.java`、`ProductDisplayRuleService.java`、`ProductDisplayRuleServiceTest.java`、`application.yml`、`docker-compose.real-pre.yml`、`.env.real-pre.example`、`remote-deploy.md`、`deploy-remote.ps1`。
- P-FIX-002 只读对账：本地 7284 快照 / 7284 运营状态 / 1963 DISPLAYING / 4566 HIDDEN / 755 PENDING / 无重复 DISPLAYING / 716 推广中但未展示；商品库 API total=1963 与 SQL DISPLAYING 一致。
- P-FIX-002D 运行态对账：重启后同步执行，本地 7323 快照 / 2377 DISPLAYING / 4575 HIDDEN / 371 PENDING / 无重复；API total=2377 与 SQL 一致。
- P-FIX-002D-REMOTE 远端对账：远端 3846 快照 / 604 DISPLAYING / 1114 HIDDEN / 2128 PENDING / 无重复；API total=604 与 SQL 一致。远端 DISPLAYING 从 420 增长到 604（+184）。
- P-DIAG-002 报告路径：`harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md`。
- P-FIX-002A 报告路径：`harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`。
- P-FIX-001C 报告路径：`harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`。
- FUNC-001 报告路径：`harness/reports/func-001-product-card-hover-ui-20260603-111451.md`。
- GIT-BATCH-2 报告路径：`harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md`。
- PRODUCT-LIBRARY-FULL-BACKFILL-FIX-001 报告路径：`harness/reports/evidence-20260615-114620.md`；`harness/reports/product-library-full-backfill-evidence-20260615.md`。
- 当前风险：1) 本地 PENDING 总量 371，远端 PENDING 总量 2128，均需后续区分过期活动、未选中和待重算来源；2) 远端 env 手工补齐了 `PRODUCT_ACTIVITY_SYNC_ENABLED` 和 `PRODUCT_ACTIVITY_SYNC_CRON`，需纳入部署文档；3) Gitee 双 remote 同步流程需确保每次推送同时 push 到 gitee；4) 商品库历史真实补数尚未执行，当前只能声明“同步系统修复完成”，不能声明“商品库数量已补齐”。
- 待优化能力：活动商品状态断链 repair、远端部署对齐、推广中商品自动入库。
- DDD 优化下一步：PRODUCT-LIBRARY-BACKFILL-PHASE-4-1 单活动真实 backfill（activityId=3859423，dryRun=false，confirm=true，先验收幂等和 DISPLAYING 口径）；通过后再进入 RECENT_30D 小批量 20 / 50 / 100 分阶段回补。
- 标记：P0。

## 达人域

- 当前状态：达人资料、标签、地址和跟进主链路已具备。
- 已完成能力：达人列表 / 详情、标签、地址、跟进。
- 待优化能力：认领、保护期、第三方接口证据、`gender` 筛选缺口和权限负例补齐。
- DDD 优化下一步：T-1 盘点达人域代码、接口、表和测试。
- 标记：P1。

## 寄样域

- 当前状态：申请、审批、发货和订单事件自动完成链路已具备。TALENT-ADDRESS-SAMPLE-DEFAULT 达人寄样地址默认保存已完成（2026-06-03），real-pre 仍依赖真实归因订单样本。
- 已完成能力：寄样申请、审批、发货、状态日志、订单事件消费；**地址默认保存**（寄样成功后回写 `talent_claim`，下次选达人自动带入，修改后更新，历史快照不变，多渠道隔离）。
- TALENT-ADDRESS-SAMPLE-DEFAULT 报告路径：`harness/reports/talent-address-sample-default-20260603-224000.md`。
- TALENT-ADDRESS-SAMPLE-DEFAULT 修改文件：`ProductQuickSampleService.java`、`SampleApplicationService.java`（后端回写）；`QuickSampleModal.vue`、`SampleCreateModal.vue`（前端加载+提交）；测试 4 文件 8 用例。
- 待优化能力：状态机完整验证、交作业命中条件、重复消费幂等和真实样本证据补齐。
- DDD 优化下一步：S-1 盘点寄样域代码、接口、表、状态机和测试。
- 标记：P0。

## Harness

- 当前状态：GIT-HARNESS-001 工作区治理完成（2026-06-03）。
- 已完成能力：Completion Gate (G0-G4)、Session Exit Gate、Quality Ledger、Git Intake / Exit Gate、Dirty Classification (10 种分类)、Allowed Change Set、Staged Scope Gate、Commit / Push / Deploy Commit Gate、批次提交流程 (GIT-BATCH-N)、Unknown Dirty Policy、Rollback Policy。
- 当前风险：需要把所有任务落地到 Git Intake / Exit Gate；P-FIX-002 同步配置残留 (application-real-pre.yml) 仍需独立任务收口。
- DDD 优化下一步：所有未来任务必须按 git-change-control.md 执行 12 条 Git 强约束。
- 标记：P0。
