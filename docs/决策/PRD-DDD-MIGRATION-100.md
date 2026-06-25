# PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100）

## Problem Statement

抖音团长 SaaS V1 项目当前 DDD 真实迁移率为 **9.9%**（按代码行数）。8 个业务域中：
- 3 个核心域（商品/订单/达人）仍以"god class"形式集中于 `service/` 包（`ProductService` 5,565 行、`OrderService` 1,074 行、`OrderSyncService` 1,445 行）
- 4 个域（用户/寄样/业绩/分析）业务规则散落在多个 Service 中，无清晰边界
- 跨域 Mapper 直接注入违规（业务 Service 直接 `@Autowired` 跨域 Mapper）仍未消除
- `data_scope` 解析、`pick_source_mapping`、`order_amount` 双轨等公式散落在多处 Service
- 业务 Service 内重复实现 `self/group/all` 数据范围过滤

**从用户角度**：在不破坏线上行为、不改动 API 返回结构、不引入新框架的前提下，按"小步、可回滚、可验证"原则逐 Service 迁移到 DDD 9 层结构，最终达成 100% DDD 化。

## Solution

按"小步推进 + 行为 1:1 等价 + 灰度开关"策略，分 14 个 Phase 完成 DDD 迁移。每个 Phase：
1. 选定 1 个最小垂直切片（1-2 个 Service 或 1 类横切关注点）
2. 先写防护测试（含行为等价性测试）
3. 引入 DDD 对象（Policy / Facade / Port / ApplicationService）
4. 在新 session 用 `/implement` 验证行为 1:1
5. 灰度开关切换 → 观察 → 清理

## User Stories

### Phase 1: 数据范围策略收口（DDD-USER-DATASCOPE 系列）

1. As a **用户域开发者**, I want to consolidate `self/group/all` filtering logic into a single `DataScopePolicy`, so that 业务 Service 不再各自实现数据范围解析。

2. As a **订单域开发者**, I want `OrderController.applyDataScope` to delegate to `DataScopePolicy`, so that 订单 Controller 不再持有 14 个参数的过滤逻辑。

3. As a **订单域开发者**, I want `OrderService.applyDataScope` to delegate to `DataScopePolicy`, so that 业务 Service 与 Controller 共用同一策略。

4. As a **订单域开发者**, I want `LegacyOrderDomainFacade.applyDataScope` to delegate to `DataScopePolicy`, so that Facade 层也消除散落。

5. As a **用户域开发者**, I want `SysUserService.applyDataScopeFilter` to delegate to `DataScopePolicy`, so that 用户域内部也用 Policy。

6. As a **运维**, I want `OrderController.applyDataScope` / `applyQueryDataScope` 私有方法被删除（完全委托 Policy），so that 重复实现被消除。

7. As a **架构师**, I want a `ddd.refactor.user-datascope.enabled` 灰度开关, so that Policy 接入可灰度切换。

### Phase 2: 用户域迁移（DDD-USER-MIGRATION 系列）

8. As a **用户域开发者**, I want `SysUserService` (1,412 行) 被拆为 5 个 ApplicationService (User CRUD / 用户组分配 / 渠道分配 / 数据权限), so that god class 被拆分。

9. As a **用户域开发者**, I want `OrgStructureService` (451 行) 被迁移到 `domain.user.policy/`, so that 组织归属解析成为可复用 Policy。

10. As a **用户域开发者**, I want `SysDeptService` (439 行) 被迁移到 `domain.user.application/`, so that 部门 CRUD 成为 ApplicationService。

11. As a **用户域开发者**, I want `SysRoleService` + `SysMenuService` 被迁移到 `domain.user.application/`, so that 角色/菜单管理成为 ApplicationService。

### Phase 3: 商品域 god class 拆解（DDD-PRODUCT-001）

12. As a **商品域开发者**, I want `ProductService` (5,565 行) 拆为 5 个 ApplicationService (同步 / 展示规则 / 业务状态 / 操作日志 / 快照), so that god class 被消除。

13. As a **商品域开发者**, I want `ProductActivityBackfillService` (1,556 行) 拆为 2-3 个 Component, so that 回填逻辑可独立测试。

14. As a **商品域开发者**, I want `ProductDisplayRuleService` (1,398 行) 拆为 Policy + ApplicationService, so that 展示规则成为可复用 Policy。

### Phase 4: 订单域迁移（DDD-ORDER-MIGRATION 系列）

15. As a **订单域开发者**, I want `OrderSyncService` (1,445 行) 拆为 4 个 Component (Dispatcher / LockManager / CheckpointService / CircuitBreaker), so that 同步引擎解耦。

16. As a **订单域开发者**, I want 3 个 DryRun Service (1603/2704/6468) 拆为 Policy + ApplicationService, so that 测试逻辑可复用。

17. As a **订单域开发者**, I want `OrderDualTrackAmountResolver` (661 行) 迁移到 `domain.order.policy/`, so that 双轨金额成为 Policy。

### Phase 5: 其他域迁移

18. As a **寄样域开发者**, I want 寄样域核心 Service (83 文件 / 12,871 行) 迁移到 `domain.sample.*`, so that 寄样域 DDD 化。

19. As a **达人域开发者**, I want 达人域核心 Service (98 文件 / 15,302 行) 迁移到 `domain.talent.*`, so that 达人域 DDD 化。

20. As a **业绩域开发者**, I want 业绩域核心 Service (41 文件 / 5,373 行) 迁移到 `domain.performance.*`, so that 业绩域 DDD 化。

21. As a **配置域开发者**, I want 配置域核心 Service (117 文件 / 9,272 行, 包括 RuleCenter / ConfigDomain / BusinessRuleConfig) 迁移到 `domain.config.*`, so that 配置域 DDD 化。

22. As a **分析开发者**, I want 分析模块核心 Service (12 文件 / 2,239 行) 迁移到 `domain.analytics.*`, so that 分析模块 DDD 化。

### Phase 6: 9 层 DDD 结构补全

23. As a **架构师**, I want 每个域补全 `api/` 层（HTTP 适配）, so that DTO 与 Controller 解耦。

24. As a **架构师**, I want 每个域补全 `query/` 层（只读视图组装器）, so that CQRS 完整化。

25. As a **架构师**, I want 每个域补全 `domain/` 层（聚合根 / 值对象）, so that 领域模型独立。

26. As a **架构师**, I want `domain/shared/` 收纳跨域复用的基础设施 (如 `DataScopePolicy`), so that 减少重复实现。

### Phase 7: 清理与验证

27. As a **架构师**, I want 删除所有 `Legacy*Facade` 类（在 `LegacyFacade` 完全替代老实现后）, so that 仓库清爽。

28. As a **架构师**, I want 所有域的 `port/` 层补全（Hexagonal Architecture）, so that 适配器可替换。

29. As a **测试**, I want 在每个 Phase 完成后跑全量回归测试, so that 无回归。

30. As a **运维**, I want DDD 迁移率从 9.9% 提升至 100% 的可量化指标, so that 项目整体健康。

## Implementation Decisions

### 模块设计

#### DDD 9 层结构（已完成部分）
- `api/` —— HTTP 适配层（DTO / Adapter），用户域和寄样域已建
- `application/` —— 应用服务（业务用例编排）
- `domain/` —— 领域模型（聚合根 / 值对象）
- `event/` —— 领域事件 + 事件发布器
- `facade/` —— 跨域门面 + Legacy 实现
- `infrastructure/` —— 基础设施适配器（port 实现）
- `policy/` —— 业务策略（纯逻辑）
- `port/` —— 端口定义（仅商品域用过）
- `query/` —— 查询层（只读视图 + 组装器）

#### 数据范围 Policy 模式（试验田）
- `DataScopePolicy` 位于 `domain/user/policy/`
- API：`decide()` 返回 Decision 枚举 + `applyTo()` 修改 wrapper + `buildFilter()` 返回 SQL 片段
- 调用方根据 Decision 自行调用 `wrapper.eq()`（因 MyBatis-Plus `Wrapper.apply` 是 protected）

#### 灰度路由器模式
- 每个迁移组件配套 Router：`XxxRouter` + `DddRefactorProperties.getXxx().isEnabled()`
- 双开关 AND 才生效：根开关 `enabled` + 子开关
- Router 暴露 3 方法：`xxx()`（灰度）+ `xxxDdd()`（强制 DDD）+ `xxxLegacy()`（强制 Legacy）—— 强制方法便于对比测试

### 架构决策（继承现有 ADR）

| ADR | 内容 | 对本 PRD 的影响 |
|---|---|---|
| ADR-003 | 订单域只存事实，不算提成 | 订单域 Service 拆分时不得抽任何"提成"计算逻辑 |
| ADR-004 | 业绩域负责最终归属 | 订单域 → 业绩域通过事件解耦 |
| ADR-005 | 分析模块只读汇总 | 分析模块不允许直接注入跨域 Mapper |
| ADR-009 | 订单金额双轨结算口径冻结 | 双轨金额 Policy 必须冻结原口径，不允许改动 |
| ADR-010 | 仓库阶段口径拍板为 V2 | 仅 V1 范围内的功能需要 DDD 化 |

### 接口契约

- **API 返回结构不变**：所有 Controller 的 response DTO 字段、类型、嵌套结构保持原样
- **DTO 位置可调整**：从 `dto/` 全局迁移到 `domain/{x}/facade/dto/`，但字段不变
- **Service 方法签名保持兼容**：外部调用点（如 Controller、其他 Service）零改动
- **Mapper 行为不变**：所有 SQL 不改写（包括 `wrapper.eq` / `wrapper.apply` 风格）

### 灰度策略

```yaml
ddd:
  refactor:
    enabled: false                              # 根开关
    user-datascope:
      enabled: false                            # DataScope 子开关
    colonel-partner-contact:
      enabled: false                            # ColonelPartner 子开关（试验田）
    # ... 每个迁移组件一个子开关
```

- **OFF（默认）**：完全走老实现
- **ON**：走 DDD 实现
- **强制方法**（如 `updateContactInfoDdd()` / `updateContactInfoLegacy()`）用于对比测试，不受开关控制

### Schema 变更

**无 schema 变更**。所有 DDD 化只调整代码组织，不动数据库。

## Testing Decisions

### 测试类型与原则

| 测试类型 | 目标 | 数量目标 |
|---|---|---|
| **单元测试**（Policy） | 覆盖所有分支（normal / null / 空） | 100% 行覆盖 |
| **行为等价性测试**（Parity） | 对照老实现的行为，证明 1:1 一致 | 每个 Policy 至少 8 个组合 |
| **回归测试**（Controller） | 验证接入后老接口不变 | 现有测试全过 |
| **集成测试**（可选） | 真实环境灰度前最后验证 | 视 Phase 复杂度而定 |

### "好测试"的定义

- **只测外部行为**：SQL 输出、返回值、异常类型 —— 不测实现细节
- **不测 lambda cache**（MyBatis-Plus 限制）：Policy 用字符串而非 SFunction
- **Parity 测试是核心交付物**：证明 Policy 与老 switch 实现的行为 1:1

### 各模块测试策略

| 模块 | 测试类型 | 优先复用 |
|---|---|---|
| DataScopePolicy | 单元 + Parity | 参考 `DataScopePolicyTest` + `DataScopePolicyParityTest` |
| ColonelPartnerContactUpdate | 单元 + Adapter + Router + Parity | 参考 `ColonelPartnerAdminServiceTest`（既有）+ 新增 4 个测试类 |
| SysUserService 拆分 | 5 个 ApplicationService 各自单元测试 | 参考 `SysUserServiceTest`（既有，26 个用例） |
| ProductService 拆分 | 5 个 ApplicationService 各自单元测试 | 参考 `ProductServiceTest`（既有，需先确认） |
| OrderSyncService 拆分 | 4 个 Component 各自单元测试 | 参考 `OrderSyncServiceTest`（既有） |

### 测试最低门槛

- 每个 Phase 必须新增 ≥ 1 个 Parity 测试
- 涉及 Controller 接入的 Phase 必须保证现有 ControllerTest **全过**
- 任何删除老代码的 Phase 必须有 **已上生产 1 周且灰度 100%** 的前置证据

## Out of Scope

### 不在本次 PRD 范围

1. **新功能开发** —— DDD 化是重构，不引入新功能
2. **API 字段新增/删除/重命名** —— 所有 response 字段冻结
3. **数据库 schema 变更** —— 不动 DDL
4. **跨域业务规则重新设计** —— 保持现有业务规则，仅调整代码组织
5. **前端重构** —— 前端 Vue 不在本 PRD 范围（除非 API 变更需要前端配合，但本 PRD 不改 API）
6. **测试覆盖率从 0% → 80%** —— 这是另一个独立工程，本 PRD 仅保证"已迁代码有测试"
7. **CI/CD 改造** —— 部署流水线不在本 PRD 范围
8. **V2.2 能力** —— 仅 V1 范围内（按 ADR-010）
9. **FastAPI / Celery 等历史方案复活** —— 不允许
10. **DDD 之外的重构**（如换框架、换数据库）—— 禁止

### 暂停条件（如出现则本 PRD 整体暂停）

- 任一 Phase 灰度 1 周内线上 P99 延迟增加 > 10%
- 任一 Phase 出现 P0/P1 缺陷需要回滚
- 数据不一致（订单事实、业绩归属、商品库）出现
- 团队无法投入每周 ≥ 5 小时（DDD 化需要持续精力）

## Further Notes

### 推进节奏

- **每轮会话**：1 个 Phase 或 1 个最小垂直切片
- **每个 Phase**：1-3 小时（设计 + 测试 + 接入 + 验证）
- **总预计**：14 Phase × 平均 1.5 天 ≈ 21 个工作日（按团队 1 人兼职）
- **建议**：分散在 2-3 个月内，避免一次性大改

### 风险提示

1. **god class 拆解是最大风险**：`ProductService` 5,565 行 / `OrderSyncService` 1,445 行 —— 测试覆盖可能不足
2. **跨域依赖**：商品域依赖订单域（pick_source_mapping）、订单域依赖配置域（rate）—— 拆解时需保留 Facade
3. **灰度期不可绕过**：每个 Phase 必须有 ≥ 1 周灰度观察期
4. **测试覆盖率不均**：很多 Service 无测试，需要先补测试再迁移

### 已有试验田（可直接复用模式）

| 试验田 | 路径 | 状态 |
|---|---|---|
| DataScopePolicy | `domain/user/policy/DataScopePolicy.java` | ✅ Phase 1 完成（Policy + Test） |
| DataScopePolicy 接入 OrderController | `controller/OrderController.java:1316` | 🟡 Phase 2 进行中（编译通过，测试未验证） |
| ColonelPartnerContactUpdate | `domain/colonel/` | ✅ Phase 0 试验田完成（上次会话） |

### 给下一位 agent 的接力清单

1. **第一动作**：跑 `mvn test -Dtest='OrderControllerTest,DataScopePolicyTest,DataScopePolicyParityTest'` 验证
2. **如果失败**：`git checkout -- backend/src/main/java/com/colonel/saas/controller/OrderController.java`
3. **如果通过**：进入 Phase 1 后续步骤（接 service/OrderService.applyDataScope）
4. **每条 issue 在新 session 用 `/implement`** 实现（按 ask-matt 主流程）

### 相关文档

- `AGENTS.md`（项目根）
- `CONTEXT.md`（项目根）
- `docs/agents/issue-tracker.md`
- `docs/agents/triage-labels.md`
- `docs/agents/domain.md`
- `docs/决策/ADR-001~010.md`
- `domain/user/policy/INTEGRATION_PLAN.md`（本 PRD Phase 1 设计依据）
- `domain/colonel/README.md`（试验田 README）

### 发布到 issue tracker

按 `docs/agents/issue-tracker.md` 约定：
```bash
gh issue create --title "PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100）" \
  --body-file <this-prd-file> \
  --label "ready-for-agent"
```

应用 `ready-for-agent` triage 标签（按 `docs/agents/triage-labels.md`）。