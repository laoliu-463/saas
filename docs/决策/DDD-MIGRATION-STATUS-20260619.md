# DDD-MIGRATION-100 状态快照（2026-06-19）

> 本文档是 DDD 重构的当前真实状态快照，供下一位 agent / 你下次启动 DDD 化工作时快速对齐。
> 按 ask-matt 的 Context hygiene 原则，这是 "/handoff" 等价的工作。

## 一、整体进度

```
项目总代码（service + controller + domain）：70,181 行
DDD 新代码：14,856 行（21.2%）
老位置业务代码：55,323 行（78.8%）
业务代码迁移率：23.3%
```

## 二、按域迁移率

| 域 | DDD 新代码 | 老位置 | 迁移率 | 状态 |
|---|---|---|---|---|
| colonel | 296 行 | 0 行 | 100.0% | ✅ 试验田完成 |
| config | 673 行 | 1,028 行 | 39.6% | 🟡 推进中 |
| analytics | 556 行 | 1,328 行 | 29.5% | 🟡 |
| sample | 1,985 行 | 6,017 行 | 24.8% | 🟡 |
| performance | 945 行 | 2,916 行 | 24.5% | 🟡 |
| order | 3,443 行 | 11,158 行 | 23.6% | 🟡 |
| product | 2,777 行 | 9,023 行 | 23.5% | 🟡 |
| user | 1,619 行 | 5,551 行 | 22.6% | 🟡 |
| talent | 1,094 行 | 6,975 行 | 13.6% | 🟡 最低 |

## 三、已完成 Issues（GitHub）

8 个 CLOSED：
- #5/#6/#7: DataScope Policy 接入 OrderController / OrderService / LegacyOrderDomainFacade
- #9/#10/#11/#12/#13: OrgStructureService DDD 化（4 个 Policy + 1 个 ApplicationService）
- Legacy shell：OrgStructureService 451 → 143 行

3 个 OPEN：
- #3: PRD 总览
- #8: DDD-USER-DATASCOPE-006 删除 OrderController 旧方法（**ready-for-human**，需灰度 1 周后人工）
- #14: DDD-USER-MIGRATION-006 SysDeptService 迁移（**进行中但未真正完成**）

## 四、本会话产出

### DDD 新代码（落到 working tree）
- domain/user/policy/DataScopePolicy.java (236 行)
- domain/user/policy/OrgAssignmentPolicy.java (136 行)
- domain/user/policy/OrgValidationPolicy.java (142 行)
- domain/user/policy/OrgEnrichmentPolicy.java (151 行)
- domain/user/application/OrgStructureApplicationService.java (110 行)
- 合计：**775 行 DDD 新代码**

### 老位置代码减少
- OrgStructureService: 451 → 143 行（**-308 行，-68%**）
- OrderController: -23 行（switch → Policy 委派）
- OrderService: -36 行
- LegacyOrderDomainFacade: -36 行

### 新增测试代码
- 9 个测试文件 / 2,144 行
- 78+ 测试用例

## 五、未完成事项 + 阻塞

### 立即可做（最低风险）

1. **修复 #14 SysDeptService 迁移**
   - 当前状态：测试基线已建（12 用例），但 SysDeptApplicationService 创建失败
   - 已删除占位符版本
   - 需要：完整复制 service/SysDeptService 全部 7 个方法到 ApplicationService

2. **#8 删除 OrderController 旧方法**（需人工）
   - 需要灰度 1 周
   - 然后人工删除

### 中优先级

3. **拆分 SysUserService 1,412 行**（PRD Phase 2 Story 8）—— 最大 god class
4. **迁移 SysRoleService + SysMenuService**（PRD Phase 2 Story 11）
5. **baseline 回归修复**：SysUserServiceTest 7 个失败 + DddQueryLayer 1 个失败

### 低优先级（更高价值）

6. **补充 9 层 DDD 缺层**：
   - domain 层（聚合根/值对象）几乎为空
   - query 层（除订单域外）
   - port 层（除商品域外）

## 六、关键技术沉淀

### DDD 模式（已验证可用）

**Policy 模式**（适用于纯逻辑）：
```java
@Component
public class OrgAssignmentPolicy {
    private final SysDeptMapper sysDeptMapper;
    // 纯函数方法
}
```

**ApplicationService 模式**（用于编排）：
```java
@Service
public class OrgStructureApplicationService {
    private final OrgAssignmentPolicy orgAssignmentPolicy;
    private final OrgValidationPolicy orgValidationPolicy;
    private final OrgEnrichmentPolicy orgEnrichmentPolicy;
    // 8 个 public 方法委托给 Policy
}
```

**Legacy Shell 模式**（向后兼容）：
```java
@Service("legacySysDeptService")  // 保留 Bean 名
public class SysDeptService {
    private final SysDeptApplicationService applicationService;
    // 所有方法薄壳委派
}
```

### 测试模式

- **Parity Test**：复制老实现的逻辑到 Policy 测试，验证 1:1 行为等价
- **Behavior Test**：跨 Service + Policy 同时跑，验证集成行为

### 灰度模式

`DddRefactorProperties` 提供 `enabled` 开关，Policy 通过 Router 接入调用点（参考 DataScopePolicy）。

## 七、约束与红线

- ✅ 不改 API 返回结构
- ✅ 不改数据库 schema
- ✅ 不删已有业务功能
- ✅ 不让订单域调用业绩计算
- ✅ 不让分析模块重算业绩归属
- ✅ 不引入新框架
- ✅ 改动文件 ≤10，单文件 ≤200 行（PRD #3 约束）
- ✅ 失败立即 `git checkout` 回滚

## 八、用户决策记录

- 用户明确选择 "**A**" 继续 DDD 主线
- 用户明确拒绝 "B" 处理 baseline 回归
- 用户明确拒绝 "C" 暂停 DDD 沉淀经验
- 用户要求 "**小步逐步推进**"
- 用户明确要求 "**2 个月内完成**"（这是 deadline 约束）

## 九、关键文件路径

- PRD: `docs/决策/PRD-DDD-MIGRATION-100.md`
- Phase 1 推进手册: `docs/harness-maintenance/engineering/PHASE-1-DDD-USER-DATASCOPE.md`
- Issue 镜像: `docs/harness-maintenance/engineering/issues-index.md`
- Skill 配置: `docs/harness-maintenance/engineering/{issue-tracker,triage-labels,context,issues-index,README}.md`

## 十、用户最后已知意图

用户要求："**重新评估时间，争取缩短两个月内完成**"。

下一步：制定 2 个月冲刺计划 + 创建冲刺 Issues。