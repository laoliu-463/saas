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

- 当前状态：订单事实、退款事实、同步日志和归因输入已具备；real-pre 真实渠道归因样本不足。
- 已完成能力：订单同步、订单入库、退款事实、归因输入保存。
- 待优化能力：`raw_payload`、`pick_source`、`colonel_buyin_id`、默认归因、事件和幂等证据补齐。
- DDD 优化下一步：O-1 盘点订单域代码、接口、表、同步任务和测试。
- 标记：P0。

## 业绩域

- 当前状态：最终归属、提成、冲正和汇总主链路已具备。
- 已完成能力：`performance_records`、最终归属、提成、冲正、汇总刷新。
- 待优化能力：输入追溯、重复消费幂等、冲正证据、权限数据范围和 dashboard 对账补齐。
- DDD 优化下一步：Y-1 盘点业绩域代码、接口、表、任务和测试。
- 标记：P0。

## 分析模块

- 当前状态：dashboard、报表和只读汇总主链路已具备。
- 已完成能力：看板汇总、报表查询、导出能力。
- 待优化能力：只读边界审查、dashboard API/SQL 对账、admin/group/self 差异验证。
- DDD 优化下一步：A-1 盘点分析模块代码、接口、表和测试。
- 标记：P0。

## 商品域

- 当前状态：商品库、活动商品同步、转链和映射主链路已具备。FUNC-001 卡片改造已完成。P-FIX-001C 分页弱化已完成。P-DIAG-002 商品库数量不足排查已完成。P-FIX-002A 同步任务 5 分钟周期配置已完成。P-FIX-002 代码与配置准备已完成。P-FIX-002D 本地运行态验证已完成。P-FIX-002D-REMOTE 远端部署验证已完成（2026-06-03），远端 commit=dea06e4c，同步参数生效，两个周期零冲突。GIT-BATCH-2 frontend-product-ui 已于 2026-06-03 14:08 提交并部署（commit=5fe6ba23）。
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
- 当前风险：1) 本地 PENDING 总量 371，远端 PENDING 总量 2128，均需后续区分过期活动、未选中和待重算来源；2) 远端 env 手工补齐了 `PRODUCT_ACTIVITY_SYNC_ENABLED` 和 `PRODUCT_ACTIVITY_SYNC_CRON`，需纳入部署文档；3) Gitee 双 remote 同步流程需确保每次推送同时 push 到 gitee。
- 待优化能力：活动商品状态断链 repair、远端部署对齐、推广中商品自动入库。
- DDD 优化下一步：建议执行 P-VERIFY-002 复核远端商品库数量（1-2 小时后），然后 P-FIX-002E repair 剩余 PENDING 商品（本地 + 远端）。
- 标记：P0。

## 达人域

- 当前状态：达人资料、标签、地址和跟进主链路已具备。
- 已完成能力：达人列表 / 详情、标签、地址、跟进。
- 待优化能力：认领、保护期、第三方接口证据、`gender` 筛选缺口和权限负例补齐。
- DDD 优化下一步：T-1 盘点达人域代码、接口、表和测试。
- 标记：P1。

## 寄样域

- 当前状态：申请、审批、发货和订单事件自动完成链路已具备，real-pre 仍依赖真实归因订单样本。
- 已完成能力：寄样申请、审批、发货、状态日志、订单事件消费。
- 待优化能力：状态机完整验证、交作业命中条件、重复消费幂等和真实样本证据补齐。
- DDD 优化下一步：S-1 盘点寄样域代码、接口、表、状态机和测试。
- 标记：P0。

## Harness

- 当前状态：GIT-HARNESS-001 工作区治理完成（2026-06-03）。
- 已完成能力：Completion Gate (G0-G4)、Session Exit Gate、Quality Ledger、Git Intake / Exit Gate、Dirty Classification (10 种分类)、Allowed Change Set、Staged Scope Gate、Commit / Push / Deploy Commit Gate、批次提交流程 (GIT-BATCH-N)、Unknown Dirty Policy、Rollback Policy。
- 当前风险：需要把所有任务落地到 Git Intake / Exit Gate；P-FIX-002 同步配置残留 (application-real-pre.yml) 仍需独立任务收口。
- DDD 优化下一步：所有未来任务必须按 git-change-control.md 执行 12 条 Git 强约束。
- 标记：P0。
