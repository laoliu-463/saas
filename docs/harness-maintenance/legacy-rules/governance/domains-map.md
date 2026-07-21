# Domain Map

## 领域总表

| 领域 | 主源文档 | 职责 | 禁止越界 | 关键证据 |
| --- | --- | --- | --- | --- |
| 用户域 | `docs/领域/用户域.md` | 用户、角色、组织、菜单、`self/group/all` | 不计算业务归属 | `/api/auth/me`、用户 API、权限 E2E |
| 配置域 | `docs/领域/配置域.md` | 配置、规则参数、审计 | 不执行业务规则 | 配置 API、配置变更日志 |
| 商品域 | `docs/领域/商品域.md` | 商品库、活动商品、转链、`pick_source_mapping` | 不计算提成 | 商品 API、同步日志、映射表 |
| 达人域 | `docs/领域/达人域.md` | 达人资料、标签、地址、跟进 | 不替代订单归属 | 达人 API、跟进日志 |
| 寄样域 | `docs/领域/寄样域.md` | 申请、审批、发货、交作业 | 不直接判定业绩 | 寄样 API、状态日志、订单事件 |
| 订单域 | `docs/领域/订单域.md` | 订单事实、退款事实、归因输入 | 不算提成、不做最终归属 | 订单表、同步日志、订单 API |
| 业绩域 | `docs/领域/业绩域.md` | 最终归属、提成、冲正、汇总 | 不采集第三方事实 | 业绩明细、汇总表、计算日志 |
| 分析模块 | `docs/领域/分析模块.md` | dashboard、报表、导出、只读汇总 | 不重算业绩归属 | dashboard API、汇总 SQL、E2E |

## 三条主链

### 渠道链

商品域 -> 达人域 -> 寄样域 -> 订单域 -> 业绩域 -> 分析模块。

关键验收：

```text
商品库可选品
-> 系统转链生成归因
-> 真实订单带回归因
-> 订单写入渠道 / 招商默认归属
-> 寄样自动完成
-> 业绩和看板归属正确
```

### 招商链

商品域 -> 达人域 -> 寄样域 -> 订单域 -> 业绩域。

关键验收：活动商品同步、推广中自动入库、审核寄样、订单回流、招商业绩。

### 管理链

用户域 -> 配置域 -> 各业务域 -> 分析模块。

关键验收：admin/group/self 数据范围、配置读取、权限生效、操作审计。

## 任务到 skill 映射

| 任务 | Harness skill |
| --- | --- |
| real-pre 排障 | `docs/harness-maintenance/legacy-rules/skills/ddd/real-pre-debug.skill.md` |
| 订单归因 | `docs/harness-maintenance/legacy-rules/skills/ddd/order-attribution.skill.md` |
| 寄样状态机 | `docs/harness-maintenance/legacy-rules/skills/ddd/sample-lifecycle.skill.md` |
| 商品库 / 推广中入库 | `docs/harness-maintenance/legacy-rules/skills/ddd/product-library.skill.md` |
| 业绩 / dashboard | `docs/harness-maintenance/legacy-rules/skills/ddd/performance-dashboard.skill.md` |
| 前端体验 | `docs/harness-maintenance/legacy-rules/skills/workflow/frontend-ux.skill.md` |
| 证据报告 | `docs/harness-maintenance/legacy-rules/skills/workflow/evidence-report.skill.md` |
| 领域边界确认 | `docs/harness-maintenance/legacy-rules/skills/ddd/domain-alignment.skill.md` |
| DDD 领域优化任务 | `docs/harness-maintenance/legacy-rules/skills/ddd/ddd-domain-optimization.skill.md` |
| DDD 边界检查 | `docs/harness-maintenance/legacy-rules/skills/ddd/ddd-boundary-check.skill.md` |
| DDD 任务后同步 | `docs/harness-maintenance/legacy-rules/skills/ddd/ddd-post-task-sync.skill.md` |
| 代码审查 | `docs/harness-maintenance/legacy-rules/skills/workflow/code-review.skill.md` |
