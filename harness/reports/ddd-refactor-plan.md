# DDD 渐进式重构总计划

更新时间：2026-06-09  
任务基线：DDD-BASE-001  
环境：real-pre（默认工程修改环境）

## 1. 核心原则

本次重构**不做大重构**，按以下顺序推进：

1. **防护层**：冻结当前行为，补回归测试、接口契约测试、金额对账测试、权限范围测试。
2. **边界层**：为每个领域建立 `ApplicationService / DomainService / Policy / Repository / Gateway / Facade` 最小骨架，不立刻迁移全部代码。
3. **隔离层**：跨域调用先走 Facade/Port，禁止新增跨域 Mapper 直连。
4. **迁移层**：从 God Service 逐段迁移纯业务逻辑，每段迁移后 API 响应不变。
5. **切换层**：通过 `ddd.refactor.*` 开关或内部委派逐步切到新实现。
6. **清理层**：测试、real-pre smoke、数据对账全部通过后，才删除旧路径。

## 2. 领域边界（不变量）

| 领域 | 职责 | 禁止 |
| --- | --- | --- |
| 订单域 | 存事实、默认归因、发订单事件 | 算提成、应用独家覆盖 |
| 业绩域 | 最终归属、提成、毛利、冲正、双轨金额 | 在订单域重复计算 |
| 商品域 | 活动、合作方、展示规则、转链、快速寄样入口 | 订单归属、寄样流程 |
| 寄样域 | 申请、审核、发货、完成生命周期 | 直接管商品/达人/业绩细节 |
| 用户域 | 身份、角色、组织架构、数据范围 `self/group/all` | 业务域自行重复算范围 |
| 配置域 | 规则参数、模板、变更事件 | 执行具体业务规则 |
| 分析模块 | 汇总表、看板查询 | 重算业绩归属 |

## 3. 安全开关（DDD-BASE-001）

所有开关默认 **false**，旧实现是唯一运行路径。子开关打开前必须先满足：对应 Phase 防护测试通过 + 本任务 evidence 记录。

| 配置键 | 环境变量 | 用途 |
| --- | --- | --- |
| `ddd.refactor.enabled` | `DDD_REFACTOR_ENABLED` | 重构总闸 |
| `ddd.refactor.user-scope.enabled` | `DDD_REFACTOR_USER_SCOPE_ENABLED` | 用户域数据范围 Facade |
| `ddd.refactor.order-sync.enabled` | `DDD_REFACTOR_ORDER_SYNC_ENABLED` | 订单同步应用层 |
| `ddd.refactor.order-attribution.enabled` | `DDD_REFACTOR_ORDER_ATTRIBUTION_ENABLED` | 订单默认归因 Policy |
| `ddd.refactor.performance-calc.enabled` | `DDD_REFACTOR_PERFORMANCE_CALC_ENABLED` | 业绩计算应用层 |
| `ddd.refactor.product-display.enabled` | `DDD_REFACTOR_PRODUCT_DISPLAY_ENABLED` | 商品展示 Policy |
| `ddd.refactor.sample-policy.enabled` | `DDD_REFACTOR_SAMPLE_POLICY_ENABLED` | 寄样资格/状态机 Policy |
| `ddd.refactor.analytics.shadow` | `DDD_REFACTOR_ANALYTICS_SHADOW` | Dashboard 双路径 shadow 对账（不影响用户可见数据） |

绑定类：`com.colonel.saas.config.DddRefactorProperties`  
回归测试：`DddRefactorPropertiesTest`

**回滚策略**：将对应环境变量设为 `false` 或删除覆盖项 → 重启 backend 容器 → 旧实现立即恢复；无需数据库 migration 回滚。

## 4. 全局执行红线

每个任务必须遵守：

- 不改公网接口路径、入参、出参（除非任务明确要求）。
- 不直接删旧代码；先保留旧实现并委派到新服务。
- 数据库 migration 仅允许：新增表、nullable 字段、索引；禁止 drop/rename。
- 禁止清库；禁止 real-pre 破坏性 SQL。
- 每个任务至少 targeted test；涉及接口加 API test；涉及金额加对账测试。
- 每个任务独立 commit，message 带任务编号。
- 每次只改一个领域或一个跨域边界，禁止顺手重构。
- 禁止引入真实 MQ（Outbox 仅建表 + 同步写 stub，Phase 9 再演进）。

## 5. 每任务统一验收

完成后必须执行并记录：

1. `git status`
2. 后端 targeted tests
3. 后端全量 `mvn test`
4. 前端受影响 targeted tests（如涉及前端）
5. 构建：`mvn package` / `npm run build`（如涉及前端）
6. 重启对应 real-pre 容器，确认 postgres/redis/backend/frontend healthy
7. smoke：`/api/system/health`、`/healthz`、登录、受影响 API
8. 数据校验：金额 API vs DB；权限三角色；事件幂等
9. 生成 `harness/reports/<task-id>-<date>.md` 或 `evidence-*.md`
10. 单独 commit，工作区无无关文件

## 6. 推荐执行顺序（Phase 0 → 1 入口）

| 序号 | 任务 ID | 说明 |
| ---: | --- | --- |
| 1 | DDD-BASE-001 | 安全开关与本文档 ✅ |
| 2 | DDD-BASE-002 | Characterization Tests |
| 3 | DDD-BASE-003 | 跨域依赖扫描 + ArchUnit 防回退 |
| 4 | DDD-USER-001 | UserDomainFacade |
| 5 | DDD-CONFIG-001 | ConfigDomainFacade |
| 6 | DDD-PRODUCT-001 | ProductDomainFacade |
| 7 | DDD-TALENT-001 | TalentDomainFacade |
| 8+ | 见用户提供的 Phase 1–11 任务矩阵 | 订单/业绩/寄样/分析/Outbox/瘦身/清理 |

完整任务矩阵与执行提示词见会话计划或 `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`（如已同步）。

## 7. 相关文档

- `docs/01-V1交付范围与边界.md`
- `docs/领域/*.md`
- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/reports/ddd-refactor-master-plan-001-20260608-135908.md`（架构观察快照）

## 8. DDD-BASE-001 结论

- **状态**：PASS（开关就位，默认全关，无业务路径变更）
- **风险**：误开环境变量可导致未评审代码路径生效 → 依赖 `DddRefactorPropertiesTest` + CI 全量测试兜底
- **下一步**：执行 DDD-BASE-002（Characterization Tests），完成前不得打开任何子开关
