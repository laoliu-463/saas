# Domain Status

## 作用

本文件记录 DDD 领域优化状态。它不替代 `docs/领域/*.md` 的领域合同，也不代表代码已经完成对应重构。

## 状态口径

- `P0`：影响 V1 主链路、权限、归因、业绩、寄样或 real-pre 验收。
- `P1`：影响领域边界、回归风险或主要运维效率。
- `P2`：治理、清理、补文档或体验优化。

## 用户域

- 当前状态：U-1 现状盘点、U-2 表结构与领域模型对齐、U-2.5-A dept_type 统一方案设计已完成（2026-06-03）。
- 报告路径：U-1 `harness/reports/user-domain-u1-inventory-20260603-120000.md`；U-2 `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`；U-2.5-A `harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`。
- U-2.5-A 核心结论：`DeptType.java` 与 `DeptTypes.java` 两套常量冲突成立；migration / init-db 同时存在 `recruiter/channel/dept`、`recruiter_group/channel_group/ops_group`、`BUSINESS` 和全 `department` 口径；real-pre 只读查询确认当前有效 `sys_dept.dept_type` 全部为 `department`（3 条）。
- 当前风险：依赖 `DeptType.isGroup()` 的组织归属、业务组列表/统计、组长校验和订单筛选元数据存在误判基础；多数现有业务 data_scope 仍按 `dept_id` 等值过滤，不能扩大写成所有 group 过滤已经必然为空。
- 待优化能力：dept_type 最小修复、CurrentUser record、PermissionContext 多角色并集、DataScopeResolver 统一、PermissionChecker 统一、UserDomainFacade、越权负例补齐。
- DDD 优化下一步：U-2.5-B dept_type 最小修复，完成后再进入 U-3 CurrentUser / PermissionContext 统一。
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

- 当前状态：商品库、活动商品同步、转链和映射主链路已具备，历史推广中入库仍需持续验证。
- 已完成能力：商品库、活动商品、转链、`pick_source_mapping`。
- 待优化能力：活动商品状态断链 repair、展示规则、审核补充信息和 real-pre 转链证据补齐。
- DDD 优化下一步：P-1 盘点商品域代码、接口、表、任务和测试。
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
