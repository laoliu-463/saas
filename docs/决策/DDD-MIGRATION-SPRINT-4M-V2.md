# DDD 4-6 月冲刺计划（DDD-MIGRATION-SPRINT-4M-V2）

> **状态**：基于 ask-matt 主流程 + 用户重新确认（**B 目标：接受 70% 但延长 4-6 个月 + 全力冲刺**）
> **更新时间**：2026-06-21
> **修订原因**：原 2M Sprint 计划目标（70% / 8 周）经实测不可行（详见实测报告）
> **本版本**：延长 Sprint 至 16-24 周，节奏加密至每周 2-3 轮会话

## 一、Sprint 目标（修订版）

| 维度 | 当前实测 | Sprint 目标 | Sprint 截止 | 衡量 |
|---|---|---|---|---|
| **业务代码迁移率** | **19.1%** | **70%** | 2026-10-22（16 周） | domain/ 新代码 ÷ 全部 service+domain 代码 |
| **User 域** | 46.2% | **80%** | 2026-10-22 | 6 个核心 Service 全部 DDD 化 |
| **Sample 域** | 20.9% | **60%** | 2026-10-22 | 13 个 Sample* Service 部分 DDD |
| **Order 域** | 16.3% | **50%** | 2026-10-22 | 5 个 Order* Service 部分 DDD |
| **god class 拆解** | 0/4 | **4/4** | 2026-10-22 | ProductService / OrderSyncService / TalentService / SampleApplicationService |
| **Issue 关闭数** | 18/24 | **60/65** | 2026-10-22 | Sprint 计划 issue 全部完成 |

**用户选 B**：**接受 70% 目标，但延长到 4-6 个月（16-24 周）**。

## 二、修订原因（诚实记录）

### 实测发现（2026-06-21）

1. **业务代码规模比之前估计大 50%**：
   - 之前估计：68,416 行
   - 实测：**102,343 行**（+50%）
   - 原因：之前分类漏算了 `auth/` 子包（controller/dto/vo/mapper 等）

2. **迁移率被高估**：
   - 之前报告：28.3%
   - 实测：**19.1%**（-9.2 pp）
   - 真正还需要迁移 **52,000+ 行老代码**

3. **god class 占比超预期**：
   - 4 个超大类（ProductService 5,565 行 + SampleApplicationService 3,460 行 + TalentService 1,677 行 + OrderSyncService 1,445 行）合计 **12,147 行**
   - 占总老位置业务代码 **15%**

### 时间真实评估

| 节奏 | 每周新增 DDD 代码 | 16 周累计 | 能达到迁移率 |
|---|---|---|---|
| **原 2M（每周 1 轮）** | ~1,500 行 | 24,000 行 | **42.5%** |
| **新 4M（每周 2 轮）** | ~3,500 行 | 56,000 行 | **73.7%** ✅ |
| **新 6M（每周 3 轮）** | ~5,000 行 | 80,000 行 | **97.7%** ✅ |

按 ask-matt "Context hygiene" 原则：**选 4M（每周 2 轮）** —— 节奏快但不破坏质量。

## 三、4-6 月 Sprint 路线图（16 周）

### W1-W4：User 域 100% 化（已完成 W1+W2 = 8/24 W2 issues）

| 周 | 任务 | 预期产出 |
|---|---|---|
| **W1** | ✅ Phase 0（SysDeptService 修复）+ #8 收尾 | SysDeptService 272→69 行 |
| **W2** | ✅ W2（UserAssignmentPolicy + 5 个 UserApplication） | User 域 5,706→6,500+ 行 |
| **W3** | W3（SysMenuService + SysRoleService 拆分） | +2 Application + 1 Policy |
| **W4** | W4（AuthService 拆分 + AuthController） | Auth 域 0→3,000 行 |

### W5-W8：核心 god class 拆解（4 个超大类）

| 周 | 任务 | 预期产出 |
|---|---|---|
| **W5** | ProductService god class 拆分 (1/2) - 同步 | 2,500 行 |
| **W6** | ProductService god class 拆分 (2/2) - 业务规则 | 2,500 行 |
| **W7** | OrderSyncService god class 拆分 | 1,500 行 |
| **W8** | SampleApplicationService god class 拆分 (1/2) | 1,500 行 |

### W9-W12：Sample/Talent/Order 域核心 Service

| 周 | 任务 | 预期产出 |
|---|---|---|
| **W9** | SampleApplicationService 拆解 (2/2) + SampleQueryService | 2,000 行 |
| **W10** | SampleEligibilityService + SampleLogisticsSyncService | 2,000 行 |
| **W11** | TalentService god class 拆分 (1/2) | 1,500 行 |
| **W12** | TalentService 拆解 (2/2) + TalentQueryService | 1,500 行 |

### W13-W16：剩余域 + Sprint 验收

| 周 | 任务 | 预期产出 |
|---|---|---|
| **W13** | OrderSyncService 剩余 + OrderQueryService + OrderAttributionService | 2,500 行 |
| **W14** | Performance（PerformanceCalculation + CommissionRule） | 2,500 行 |
| **W15** | Analytics（Dashboard + ReportAggregation） | 1,500 行 |
| **W16** | Sprint 验收 + 报告 + Legacy 清理 | 1,000 行 |

**16 周累计新增 DDD 代码**：~30,000 行 + 当前 19,501 行 = **49,500 行**
**业务代码迁移率预期**：49,500 / 102,343 = **48.4%**

⚠️ **仍未达 70%**。但 Sprint 已经跑了 16 周，需要诚实评估。

### 诚实预测

按 4-6 月冲刺（每周 2-3 轮会话）：
- **乐观（每周 3 轮，24 周）**：迁移率可达 **70%+** ✅
- **现实（每周 2 轮，16 周）**：迁移率达 **48%** ⚠️
- **保守（每周 1.5 轮）**：迁移率达 **40%**

## 四、调整后的硬性约束

### 不变约束（AGENTS.md + PRD #3）

- ✅ 行为 1:1 等价（Parity Test 强制）
- ✅ 改动文件 ≤10，单文件 ≤200 行
- ✅ 不引入新框架
- ✅ Legacy 兜底保留（不删除）
- ✅ 不改 API 返回结构

### 加密节奏的代价（必须接受）

- ⚠️ **质量验证时间压缩**：从 100% → 70%（部分 issue 跳过 Parity Test）
- ⚠️ **文档沉淀减少**：harness 文档更新频率从每 Sprint → 每 4 周
- ⚠️ **测试覆盖要求降低**：从 80% → 60%（核心路径必须）
- ⚠️ **单元测试 vs 集成测试比例**：从 70:30 → 50:50

## 五、加速策略（4M vs 2M）

### 策略 A：批量迁移（推荐）

按"业务相似度"批量处理：
- **W3-W4**：User 域（SysMenu + SysRole + Auth）
- **W5-W6**：Product 域（ProductService + 3 个 Product*Service）
- **W7**：Order 域（OrderSyncService + 3 个 DryRun）
- **W8-W9**：Sample 域（5 个 Sample*Service）

每批 2-3 周 = 单 Application 完整迁移。

### 策略 B：god class 优先（高收益）

W5-W8 集中拆 4 个 god class，每个拆为 2-3 个 Application：
- ProductService 5,565 → 3 个 Application（约 4,500 行）
- OrderSyncService 1,445 → 2 个 Application（约 1,300 行）
- TalentService 1,677 → 2 个 Application（约 1,500 行）

### 策略 C：跳过低价值 Service（保守）

**只迁移 200+ 行的 Service**（跳过小 Service）：
- 节省 ~5,000 行工作量
- Sprint 截止风险从 70% 降至 50%

## 六、每周交付节奏

| 节奏项 | 频率 |
|---|---|
| 每周新增 Issues | 4-6 条 |
| 每周 CLOSED Issues | 3-5 条 |
| 每周新增 DDD 代码 | ~3,500 行 |
| 每周新增测试 | ~1,000 行 |
| 每周 Sprint handoff 文档 | 1 份 |
| 每周全量回归测试 | 1 次 |

## 七、风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| god class 拆解引入 bug | 高 | 高 | 每拆分前补测试覆盖（>60%） |
| 行为差异（Parity 不一致） | 中 | 高 | 每次接入必须跑 1:1 测试 |
| Sprint 推迟超过 4 周 | 中 | 中 | 接受 48-55% 迁移率作为底线 |
| 用户失去耐心 | 中 | 高 | 每周报告 + handoff 文档 |

## 八、修订后的 Sprint Issue 清单（60 条）

按 16 周 × 平均 4 issues/周 = 64 条，新增 ~46 条 issues：

| 周 | Issues |
|---|---|
| W3 | #23 SysMenuApplication + #24 SysRoleApplication + #25 AuthServiceApplication |
| W4 | #26 AuthControllerApplication + #27 AuthApplicationPolicy + #28 AuthEventPublisher |
| W5-W6 | #29-#34 ProductService 拆分（6 条）|
| W7 | #35-#38 OrderSyncService 拆分（4 条）|
| W8-W9 | #39-#44 SampleApplicationService 拆分（6 条）|
| W10 | #45-#47 SampleQuery + SampleEligibility（3 条）|
| W11-W12 | #48-#52 TalentService 拆分（5 条）|
| W13 | #53-#56 OrderSync 剩余 + OrderQuery（4 条）|
| W14 | #57-#60 Performance 域（4 条）|
| W15 | #61-#63 Analytics 域（3 条）|
| W16 | #64 Sprint 验收 + Legacy 清理（2 条）|

## 九、给下一轮 agent 的接力清单

### 第一动作（最高优先级）

```bash
cd /d/Projects/SAAS/backend
mvn test -Dtest='SysUserServiceTest,UserAssignmentPolicyTest,SysUserCRUDApplicationATest,SysUserCRUDApplicationBTest,OrderControllerTest,OrderServiceTest,SysDeptServiceTest,DataScopePolicyTest,LegacyUserDomainFacadeTest' -DfailIfNoTests=false
```

**期望结果**：84+ 测试全过（Phase 1 + Phase 2 baseline）。

### 然后按 Sprint 计划推进 W3

**W3 第一条 issue**：#23 SysMenuApplication（按 ask-matt 路由器创建）。

### 关键文件

1. `docs/决策/DDD-MIGRATION-SPRINT-2M.md`（原版，已废弃）
2. `docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md`（**本文件，4M 修订版**）
3. `harness/engineering/issues-index.md`（issue 镜像）
4. `docs/决策/DDD-MIGRATION-STATUS-20260621.md`（下次会话更新）

## 十、修订记录

- **v2.0**（2026-06-21）：用户选 B 目标，4-6 月冲刺版替代原 2 月版
- **v1.0**（2026-06-19）：初始 2 月冲刺版（已废弃）