# DDD-MIGRATION-100 状态快照（2026-06-21）

> **本文件是 DDD 重构的当前真实状态快照**，供下一位 agent / 你下次启动 DDD 化工作时快速对齐。
> 按 ask-matt 的 Context hygiene 原则，这是 `/handoff` 等价的工作。
> **版本**：v2（基于实测 19.1% 业务代码迁移率修订）

## 一、整体进度（实测 2026-06-21）

```
项目总 Java 文件:    753
项目总代码行数:      124,618 行
DDD 新代码 (domain/): 19,562 行 / 307 文件（15.7%）
非 DDD 代码:        105,056 行 / 528 文件（84.3%）

业务代码迁移率:      19.1%（修正前虚报 28.3%）
Sprint 4M 截止:      2026-10-22（16 周）
当前 Sprint:         v2 已修订（4-6 月冲刺）
```

## 二、按域迁移率（修正版）

| 域 | DDD | 老位置 | 合计 | 迁移率 | 状态 |
|---|---|---|---|---|---|
| **event** | 1,385 | 0 | 1,385 | **100%** | ✅ 完成 |
| **user** | 5,706 | 6,645 | 12,351 | **46.2%** | 🟢 最大进展 |
| colonel | 296 | 400 | 696 | 42.5% | 🟢 试验田 |
| analytics | 556 | 1,590 | 2,146 | 25.9% | 🟡 |
| sample | 2,069 | 7,811 | 9,880 | 20.9% | 🟡 |
| order | 3,443 | 17,654 | 21,097 | 16.3% | 🟡 |
| performance | 1,419 | 9,252 | 10,671 | 13.3% | 🟡 |
| product | 2,777 | 18,587 | 21,364 | 13.0% | 🟡 |
| config | 673 | 4,906 | 5,579 | 12.1% | 🟡 |
| talent | 1,094 | 14,634 | 15,728 | **7.0%** | 🔴 最低 |
| shared | 83 | 1,363 | 1,446 | 5.7% | 🔴 |
| **合计业务代码** | **19,501** | **82,842** | **102,343** | **19.1%** | — |

## 三、god class 拆解清单（迁移主要障碍）

| 类 | 行数 | Sprint 4M 计划周次 |
|---|---|---|
| **ProductService** | **5,565** | W5-W6（拆分 6 条 issues） |
| **SampleApplicationService** | **3,460** | W8-W9（拆分 6 条 issues） |
| **TalentService** | **1,677** | W11-W12（拆分 5 条 issues） |
| **OrderSyncService** | **1,445** | W7（拆分 4 条 issues） |

## 四、本会话产出（落地到 working tree）

| 文件 | 状态 |
|---|---|
| `docs/决策/DDD-MIGRATION-STATUS-20260619.md` | 🆕 Sprint 2M 状态快照（v1） |
| `docs/决策/DDD-MIGRATION-STATUS-20260621.md` | 🆕 Sprint 4M 状态快照（v2） |
| `docs/决策/DDD-MIGRATION-SPRINT-2M.md` | 🆕 Sprint 2M 计划（已废弃） |
| `docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md` | 🆕 Sprint 4M 计划（当前） |
| `domain/user/application/SysDeptApplicationService.java` | 🆕 296 行（Sprint W1 产出） |
| `service/SysDeptService.java` | ✏️ 272→69 行（Legacy 委派壳） |
| `domain/user/policy/UserAssignmentPolicy.java` | 🆕 231 行（Sprint W2 产出） |
| `test/.../UserAssignmentPolicyTest.java` | 🆕 13 用例 |
| `harness/engineering/issues-index.md` | ✏️ 镜像更新 |

## 五、GitHub Issues 状态

| Status | Count | Issues |
|---|---|---|
| **CLOSED** | 19 | Phase 1（3）+ Phase 2（6）+ Sprint W1（2）+ Sprint W2（6）+ Misc（2） |
| **OPEN** | 5 | #3 PRD + #22/#23/#24 (W3) + Sprint 总览 |

**关闭率**：79.2%

## 六、Sprint 4M-V2 路线图（16 周）

| 周 | 任务 | Issues |
|---|---|---|
| **W1** | ✅ Phase 0（SysDeptService 修复）+ #8 收尾 | #15/#8 |
| **W2** | ✅ W2（UserAssignmentPolicy + 5 UserApplication） | #16/#17/#18/#19/#20 |
| **W3** | 🚧 SysMenu + SysRole + Auth Application | **#22/#23/#24** |
| W4 | AuthController + AuthPolicy + AuthEventPublisher | #26-#28 |
| W5-W6 | **ProductService god class 拆分** | #29-#34 |
| W7 | **OrderSyncService god class 拆分** | #35-#38 |
| W8-W9 | **SampleApplicationService 拆分** | #39-#44 |
| W10 | SampleQuery + SampleEligibility | #45-#47 |
| W11-W12 | **TalentService 拆分** | #48-#52 |
| W13 | OrderSync 剩余 + OrderQuery | #53-#56 |
| W14 | Performance 域 | #57-#60 |
| W15 | Analytics 域 | #61-#63 |
| W16 | Sprint 验收 + Legacy 清理 | #64-#65 |

## 七、约束与红线（不变）

- ✅ 行为 1:1 等价（Parity Test 强制）
- ✅ 改动文件 ≤10，单文件 ≤200 行
- ✅ 不引入新框架
- ✅ Legacy 兜底保留
- ✅ 不改 API 返回结构

## 八、决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| Sprint 周期 | **4-6 月冲刺** | 用户选 B（接受 70% 目标但延长） |
| 节奏 | **每周 2 轮会话** | 现实可行（每周 1 轮不够） |
| 完成度定义 | **业务代码 70%+** | 用户目标（v2 Sprint） |
| 跳过 9 层 DDD 完整结构 | ✅ | 推到 Sprint 后 |
| Legacy 保留 | ✅ | 保留兜底，不删除 |
| baseline 回归修复 | ✅ Sprint 内 | 不阻塞主目标 |

## 九、关键文件路径

- PRD: `docs/决策/PRD-DDD-MIGRATION-100.md`
- **Sprint 计划（当前）**: `docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md`
- Sprint 计划（已废弃）: `docs/决策/DDD-MIGRATION-SPRINT-2M.md`
- Phase 1 推进手册: `harness/engineering/PHASE-1-DDD-USER-DATASCOPE.md`
- Issue 镜像: `harness/engineering/issues-index.md`

## 十、给下一轮 agent 的接力清单

### 第一动作（最高优先级）

```bash
cd /d/Projects/SAAS/backend
mvn test -Dtest='SysUserServiceTest,UserAssignmentPolicyTest,SysUserCRUDApplicationATest,SysUserCRUDApplicationBTest,OrderControllerTest,OrderServiceTest,SysDeptServiceTest,DataScopePolicyTest,LegacyUserDomainFacadeTest' -DfailIfNoTests=false
```

**期望结果**：84+ 测试全过（Phase 1 + Phase 2 baseline）。

### 然后按 Sprint 计划推进 W3

**W3 第一条 issue**：**#22 SysMenuApplication**。

### 关键文档（按读取顺序）

1. `docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md`（Sprint 计划 v2）
2. `docs/决策/DDD-MIGRATION-STATUS-20260621.md`（本文件）
3. `harness/engineering/issues-index.md`（issue 镜像）
4. `harness/engineering/PHASE-1-DDD-USER-DATASCOPE.md`（Phase 1 模板）

## 十一、修订记录

- **v2**（2026-06-21）：用户选 B 目标，4-6 月冲刺版替代原 2 月版
- **v1**（2026-06-19）：初始 2 月冲刺版（已废弃）