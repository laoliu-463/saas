# Phase 1 推进手册（DDD-USER-DATASCOPE）

> **状态**：按 ask-matt 主流程，第 1 步 PRD 完成，第 4 步 issues 已拆分。
> **本文件**：给下一位 agent（在 `/implement` skill 启动时）提供完整上下文。

## 当前阶段

- **PRD**：Issue #3 已发布
- **Phase 1 拆分**：4 个 issues（#5/#6/#7/#8）
- **已完成**：
  - `DataScopePolicy`（domain/user/policy/，+46 + 18 用例测试）
  - `DataScopePolicyTest`（17 用例，单元测试）
  - `DataScopePolicyParityTest`（18 用例，行为对照）
  - `INTEGRATION_PLAN.md`（接入方案设计）
  - OrderController 接入（**待测试验证**）
  - 5 个 triage 标签已创建（needs-triage/needs-info/ready-for-agent/ready-for-human/wontfix）

## 待办（按依赖顺序）

### Issue #5 [DDD-USER-DATASCOPE-003] verify OrderController integration
- **优先级**：🔴 最高
- **任务**：跑测试验证 OrderController 接入是否成功
- **命令**：
  ```bash
  cd backend
  mvn test -Dtest='OrderControllerTest,DataScopePolicyTest,DataScopePolicyParityTest' -DfailIfNoTests=false
  ```
- **验收**：46+ 测试全过
- **失败回滚**：
  ```bash
  cd /d/Projects/SAAS
  git checkout -- backend/src/main/java/com/colonel/saas/controller/OrderController.java
  ```

### Issue #6 [DDD-USER-DATASCOPE-004] 接入 service/OrderService.applyDataScope
- **依赖**：#5 通过
- **任务**：OrderService 注入 DataScopePolicy，修改 applyDataScope 委托
- **关键文件**：`backend/src/main/java/com/colonel/saas/service/OrderService.java:520`
- **新增测试**：OrderService.applyDataScope Parity 测试（至少 3 用例）

### Issue #7 [DDD-USER-DATASCOPE-005] 接入 LegacyOrderDomainFacade.applyDataScope
- **依赖**：#6 通过
- **任务**：Facade 注入 Policy
- **关键文件**：`backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java:437`

### Issue #8 [DDD-USER-DATASCOPE-006] 删除 OrderController 旧 applyDataScope
- **标签**：`ready-for-human`（**必须人工审核**）
- **依赖**：#5/#6/#7 全部通过 + 灰度 1 周
- **风险**：🟡 中
- **任务**：删除 OrderController:1316-1340 私有方法 + DddRefactorProperties.getUserDatascope() 开关

## DDD-USER-DATASCOPE 设计要点

### Policy 接口
```java
public class DataScopePolicy {
    // 决策（推荐用于 LambdaQueryWrapper）
    public Decision decide(UUID userId, UUID deptId, DataScope scope);
    // FILTER_USER / FILTER_DEPT / NO_FILTER
    
    // 直接修改 wrapper（LambdaQueryWrapper）
    public <T> void applyTo(LambdaQueryWrapper<T> wrapper, 
                            UUID userId, UUID deptId, DataScope scope,
                            SFunction<T, ?> userIdField, SFunction<T, ?> deptIdField);
    
    // 直接修改 wrapper（QueryWrapper）
    public <T> void applyTo(QueryWrapper<T> wrapper, 
                            UUID userId, UUID deptId, DataScope scope,
                            String userIdColumn, String deptIdColumn);
    
    // SQL 字符串（备选，不推荐）
    public String buildFilter(UUID userId, UUID deptId, DataScope scope,
                              String userIdColumn, String deptIdColumn);
}

public enum Decision { FILTER_USER, FILTER_DEPT, NO_FILTER }
```

### 接入模式（LambdaQueryWrapper）
```java
private void applyDataScope(LambdaQueryWrapper<...> wrapper, 
                            UUID userId, UUID deptId, DataScope dataScope) {
    DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
    switch (decision) {
        case FILTER_USER -> wrapper.eq(ColonelsettlementOrder::getUserId, userId);
        case FILTER_DEPT -> wrapper.eq(ColonelsettlementOrder::getDeptId, deptId);
        case NO_FILTER -> { /* no-op */ }
    }
}
```

### 接入模式（QueryWrapper）
```java
private void applyQueryDataScope(QueryWrapper<...> wrapper,
                                 UUID userId, UUID deptId, DataScope dataScope) {
    DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
    switch (decision) {
        case FILTER_USER -> wrapper.eq("user_id", userId);
        case FILTER_DEPT -> wrapper.eq("dept_id", deptId);
        case NO_FILTER -> { /* no-op */ }
    }
}
```

## ⚠️ 不要做的事

1. **不要用 `wrapper.apply(String, Object...)`** —— MyBatis-Plus 该方法是 protected
2. **不要省略 Parity 测试** —— 行为 1:1 是 DDD 迁移的核心
3. **不要一次接入多个调用点** —— 每个 issue 独立
4. **不要在 DDD Service 里直接调 Repository** —— 保持架构层级

## 相关文档

1. `AGENTS.md` — 项目协议
2. `CONTEXT.md` — 领域词汇
3. `docs/决策/PRD-DDD-MIGRATION-100.md` — 完整 PRD
4. `domain/user/policy/DataScopePolicy.java` — Policy 实现
5. `domain/user/policy/INTEGRATION_PLAN.md` — 接入方案
6. `harness/engineering/issues-index.md` — GitHub Issues 镜像

## 变更历史

- **v1.0**（2026-06-19）：初始化，Phase 1 推进手册"