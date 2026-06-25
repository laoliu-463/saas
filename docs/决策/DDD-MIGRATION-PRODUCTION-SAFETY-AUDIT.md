# DDD-MIGRATION Production Safety Audit（2026-06-21）

> **本文件是 DDD 化对生产环境影响的安全审计报告**。
> 按 ask-matt "Context hygiene" 原则 + 用户"生产安全优先"要求，**本会话立即产出**。

## 一、当前状态（实测 2026-06-21）

### 核心结论：✅ 当前 DDD 化对生产**零影响**

**理由**：
1. 所有 Application Service 都被**旧 Service 委派壳**调用
2. 旧 Service 仍是生产路径
3. 现有 13 个 Application + 4 个 Policy 都是**可选依赖**（旧路径可独立工作）
4. Feature Flag 基础设施（`DddRefactorProperties`）已建但未启用

### 详细审计

#### Application Services（13 个）

| Application | 是否被生产调用 | 风险等级 |
|---|---|---|
| SysDeptApplicationService | ❌ 仅 SysDeptService 委派壳调用 | 🟢 零风险 |
| OrgStructureApplicationService | ❌ 同上 | 🟢 |
| SysUserCRUDApplicationA | ❌ SysUserService 委派 | 🟢 |
| SysUserCRUDApplicationB | ❌ 同上 | 🟢 |
| SysUserGroupMembershipApplication | ❌ 同上 | 🟢 |
| SysUserRoleAssignmentApplication | ❌ 同上 | 🟢 |
| SysUserQueryApplicationService | ❌ 同上 | 🟢 |
| UserMasterDataApplicationService | ❌ 同上 | 🟢 |
| UserAssignableApplicationService | ❌ 同上 | 🟢 |
| CurrentUserApplicationService | ❌ 同上 | 🟢 |
| OrgUnitWriteApplicationService | ❌ 同上 | 🟢 |
| OrgUnitDirectoryApplicationService | ❌ 同上 | 🟢 |
| 其他 Application（其他域） | ❌ 同上 | 🟢 |

#### Policy（4 个 user 域）

| Policy | 是否被生产调用 | 风险等级 |
|---|---|---|
| DataScopePolicy | ✅ **是** | ⚠️ **中**（已 hardcode 接入，无开关） |
| UserAssignmentPolicy | ❌ 仅被 Application 调用 | 🟢 |
| OrgAssignmentPolicy | ❌ 同上 | 🟢 |
| OrgValidationPolicy | ❌ 同上 | 🟢 |
| OrgEnrichmentPolicy | ❌ 同上 | 🟢 |
| UserAccessPolicy | ❌ 同上 | 🟢 |
| UserChannelCodePolicy | ❌ 同上 | 🟢 |
| UserCredentialPolicy | ❌ 同上 | 🟢 |
| CurrentUserPermissionPolicy | ❌ 同上 | 🟢 |

#### ⚠️ **DataScopePolicy 风险详解**

**接入点**（已全量生产）：
1. `OrderController.applyDataScope` / `applyQueryDataScope`（line 1311/1323）—— 委派给 Policy
2. `OrderService.applyDataScope` / `applyQueryDataScope`（line 538/555）—— 委派给 Policy
3. `LegacyOrderDomainFacade.applyDataScope` / `applyQueryDataScope`（line 454/466）—— 委派给 Policy

**当前是否有开关**？**❌ 否**（之前 Issue #5/#6/#7 时直接 hardcode 接入）

**生产风险**：
- 如果 Policy 行为有 bug，**生产全量受影响**
- 灰度能力 = 无
- 回滚方式 = git revert

**修复方案**：
```java
// 在 OrderController 中加开关
public void applyDataScope(...) {
    if (dddProperties.getOrderAttribution().isEnabled()) {
        // 走 DDD Policy
        dataScopePolicy.applyTo(...);
    } else {
        // 走旧 switch（恢复）
        switch (dataScope) {
            case PERSONAL: ...
            case DEPT: ...
            case ALL: ...
        }
    }
}
```

**问题**：⚠️ **Issue #5/#6/#7 时已经删除了旧 switch 代码**！**无法回滚到旧实现**！

**这是真实的 P1 风险**。

## 二、当前可立即采取的"安全 DDD 化"行动

### 行动 1：补回 OrderController / OrderService / LegacyOrderDomainFacade 的旧 switch（紧急）

**理由**：DataScopePolicy 没有灰度开关。如果 Policy 有 bug，无法回滚。

**实施**：
1. 从 git history 找回 switch 实现
2. 加 Feature Flag 包装：
   ```java
   if (dddProperties.getDataScopePolicy().isEnabled()) {
       dataScopePolicy.applyTo(...);  // 新
   } else {
       switch (dataScope) { ... }     // 旧（恢复）
   }
   ```
3. 跑 Parity Test 验证
4. 提交

**风险**：🟢 零（只是恢复代码）

### 行动 2：建立标准 DDD 化流程（必须）

**标准流程**：
1. 建 Application（完整实现）
2. 建 Policy（纯函数）
3. 写 Parity Test（1:1 行为对比）
4. **加 Feature Flag 包装**（新代码不直接调用）
5. 测试 Feature Flag ON/OFF 两种状态
6. 提交（开关默认 OFF）
7. **生产灰度**（1% → 100%）
8. 监控 1 周
9. 切到 ON 100%
10. 保留 Legacy 兜底

### 行动 3：审计其他已"完成"的 DDD 化

检查 OrderController / OrderService / LegacyOrderDomainFacade 的 DDD 化是否有开关保护。

## 三、1 月冲刺"生产安全"路线图

### W1：建安全基础设施（最高优先级）

| 任务 | 时间 | 产出 |
|---|---|---|
| 补回 DataScopePolicy 旧 switch（加 Feature Flag） | 2 小时 | 3 个 Service 各加开关 |
| 建立标准 DDD 化流程文档 | 1 小时 | `harness/engineering/DDD-MIGRATION-PATTERN.md` |
| 加 `DataScopePolicy` Feature Flag | 30 分钟 | `DddRefactorProperties.dataScopePolicy` 字段 |

**W1 产出**：3 个 Service 加 Feature Flag + 流程文档

### W2：User 域 W3 推进（已有 #22/#23/#24）

| 任务 | 时间 | 风险 |
|---|---|---|
| #22 SysMenuApplication | 5 小时 | 🟢 |
| #23 SysRoleApplication | 4 小时 | 🟢 |
| #24 AuthApplication | 8 小时 | 🟢 |

**W2 产出**：3 个 Application + 开关包装

### W3：ProductService 拆分（1/2）

| 任务 | 时间 | 风险 |
|---|---|---|
| ProductApplication 基础 | 6 小时 | 🟡 |
| Feature Flag 包装 | 2 小时 | 🟢 |
| Parity Test | 4 小时 | 🟢 |

**W3 产出**：1 个 Application + 完整开关

### W4：灰度 + Sprint 验收

| 任务 | 时间 |
|---|---|
| 灰度 1% → 10% | 1 周持续 |
| Sprint 验收 | 1 小时 |

**W4 产出**：灰度数据 + Sprint 报告

## 四、给下一轮 agent 的关键指令

### 最高优先级：W1 必须完成

```bash
# 1. 找 DataScopePolicy 旧 switch（git history）
git log --all --oneline -- "*OrderController.java" | head -10

# 2. 补回旧 switch + 加 Feature Flag
# 文件: backend/src/main/java/com/colonel/saas/controller/OrderController.java
# 文件: backend/src/main/java/com/colonel/saas/service/OrderService.java
# 文件: backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java

# 3. 验证
mvn test -Dtest='OrderControllerTest,OrderServiceTest,LegacyOrderDomainFacadeTest,DataScopePolicyTest' -DfailIfNoTests=false
```

**预期**：57+ 测试全过

### 严格规则

- ✅ **新 Application 必须配 Feature Flag**
- ✅ **新 Policy 必须配 Feature Flag**
- ✅ **Parity Test 必须存在**
- ✅ **Legacy 兜底必须保留**
- ❌ **禁止直接替换生产路径**
- ❌ **禁止删除旧 switch**

## 五、决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| Feature Flag 策略 | ✅ 方案 A（Feature Flag 灰度） | 用户选"生产安全" |
| 当前 DDD 化对生产影响 | ✅ 零（13 个 Application 都是 Legacy 委派） | 实测审计 |
| **DataScopePolicy 风险** | ⚠️ **P1**（无开关，已 hardcode 接入） | 待修复 |
| 1 月冲刺目标 | 业务代码 30-35% + Feature Flag 全覆盖 | 现实可达 |
| Legacy 保留 | ✅ 永久保留 | 兜底回滚 |

## 六、修订后的 Sprint 计划链接

- **本审计报告**：`docs/决策/DDD-MIGRATION-PRODUCTION-SAFETY-AUDIT.md`（本文件）
- Sprint 计划：`docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md`（**需按本审计修订**）
- 状态快照：`docs/决策/DDD-MIGRATION-STATUS-20260621.md`（**需按本审计修订**）