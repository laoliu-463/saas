# DDD-MIGRATION-SPRINT-4M-V3 (诚实修订版)

> **本文件是 Sprint 4M-V2 的修订**。基于 06-26 真实 LOC 测量 + 4M-V2 W1-W3 实际完成节奏修订。
> 按 ask-matt 诚实原则：原 V2 用估算 LOC，V3 用实测 LOC。

## 一、真实起点 (06-26 17:00 测量)

```
Backend src/main 总 LOC: 76,175
业务代码 (排除 common/vo/dto/entity 等共享):
  DDD: 15,328 LOC (domain/ + infrastructure/) = 26.53%
  Legacy: 42,440 LOC (service/ + controller/ + auth/service/ + job/)
  业务总计: 57,768 LOC
```

按 domain 真实分布:
```
user         5,044 DDD / 26,681 Legacy = 15.9%   待迁移 17,163
order        2,818 DDD /  7,827 Legacy = 26.5%   待迁移  4,633
product      2,905 DDD /  3,827 Legacy = 43.2%   待迁移  1,807
sample       1,391 DDD /  1,301 Legacy = 51.7%   待迁移    493
talent         763 DDD /    702 Legacy = 52.1%   待迁移    262
performance    955 DDD /    148 Legacy = 86.6%   待迁移      0
analytics      384 DDD /      0 Legacy = 100%    待迁移      0
colonel         90 DDD /  1,954 Legacy = 4.4%    待迁移  1,340
config         324 DDD /      0 Legacy = 100%    待迁移      0
```

## 二、修订目标

**原 V2 目标**: 70% DDD 业务占比 (估算基础)
**V3 修订**:
- 选项 A: 保持 70% 目标，延长 Sprint 至 6 个月（按当前 W3 实际节奏）
- 选项 B: 70% → 50% 目标（已超额完成 26.5% / 50% = 53%）
- 选项 C: 暂停 Sprint，切换到新功能开发

**推荐选项 B**：50% 目标 = 13,856 LOC 待迁移，按当前节奏 4-5 周可完成，比 70% 现实。

## 三、修订 Sprint 计划 (W4-W12 = 9 周)

### W4 (本周开始) - AuthController + colonel 试验田
- W4-#1: AuthControllerApplication 创建（委派壳，~150 LOC）
- W4-#2: colonel Partner Contact Update 试验田完成（stash@{18} 验证 BehaviorParityTest）
- W4-#3: colonel Partner Query Application（试验田扩）
- 验收: AuthController 委派壳完成 + colonel 试验田 mvn test PASS

### W5-W6 - ProductService 拆分 (5,565 行)
- 5 个 issues: ProductDisplayPolicy 拆解 + ProductSearchApplication + ProductLibraryApplication + ProductPinPolicy 强化 + ProductBizStatusService
- 验证: ProductService < 1500 行

### W7 - OrderSyncService 拆分 (1,445 行)
- 4 个 issues: OrderSyncApplication 委派壳 + OrderSyncPort 抽取 + OrderSyncFacade 收口
- 验证: OrderSyncService < 400 行

### W8-W9 - SampleApplicationService 拆分 (3,460 行)
- 6 个 issues: SampleSubmitApplication + SampleReviewApplication + SampleLogisticsApplication + SampleExportApplication + SampleBoardApplication + SampleDetailApplication
- 验证: SampleApplicationService < 800 行

### W10 - SampleQuery + Eligibility
- 3 个 issues: SampleQueryApplication 委派壳 + SampleEligibilityPolicy 强化 + SampleStatusLogService 拆分

### W11-W12 - TalentService 拆分 (1,677 行)
- 5 个 issues: TalentAssignApplication + TalentQueryApplication + TalentProfileApplication + TalentTagApplication + TalentBlacklistApplication

### W13 - OrderQuery/Attribution 收尾
- 4 个 issues: OrderQueryApplication 委派壳 + OrderAttributionApplication 强化 + OrderAttributionPolicy 强化 + OrderAmountPolicy 强化

### W14-W15 - Performance + Analytics 强化
- 7 个 issues: 性能域 4 个 + 分析域 3 个 (主要 Port 抽取 + 文档)

### W16 - Sprint 验收
- 2 个 issues: 全量测试 + Legacy 清理 + 报告

## 四、节奏

按 06-19~26 实际节奏:
- W1 (1 issue) = 1 天
- W2 (6 issues) = 3 天
- W3 (3 issues) = 5 天
- 平均: **每个 issue ~1.5 天** (考虑分支切换 + Codex race + P1 干扰)

39 issues / 1.5 = 58.5 天 ≈ 8.4 周 ≈ **2 个月**

## 五、风险与缓解

| 风险 | 缓解 |
|---|---|
| Codex race condition | 错峰 commit + 每 PR review 验证 |
| 业务口径变更 | 严格 1:1 行为等价 + Parity test |
| 编号冲突 (W4 #26-#28) | 新生成 W4+ 后端 issues (#40-#80) |
| Spring 泄漏到 policy | 现有 DDD guard / ArchUnit 已守护 |
| 试验田 colonel 进度慢 | 单独 issue 跟进，不阻塞主线 |

## 六、验收

- 所有 39 issues 全部 CLOSED
- 业务 DDD 占比 ≥ 50% (从 26.5%)
- 4 个 god class 全部 < 1500 行 (ProductService / SampleApp / OrderSync / Talent)
- 零业务口径变更 (1:1 行为等价测试覆盖)
- real-pre P0 验收全过
- mvn test + vitest 全过

## 七、与 V2 的差异

| 维度 | V2 | V3 |
|---|---|---|
| Sprint 长度 | 16 周 (4 个月) | 12 周 (3 个月) |
| 目标 | 70% | 50% |
| 起点估算 | 19.1% (估算) | 26.5% (实测) |
| W4+ 编号 | 沿用 #26-#28 (冲突) | 新生成 #40-#80 |
| 总 issues | ~60 (估算) | 39 (明确) |
| Legacy 清理 | W16 统一 | W16 + 持续 |

## 八、给下一位 agent 的接力清单

### 第一动作
```bash
cd /d/Projects/SAAS
git checkout feature/ddd/DDD-VERIFY-001
gh issue create --title "Sprint-4M-V3 W4-#1 AuthControllerApplication 委派壳" --label "ready-for-agent"
```

### 关键文档 (按读取顺序)
1. `docs/决策/DDD-MIGRATION-SPRINT-4M-V3.md` (本文件)
2. `docs/决策/DDD-MIGRATION-SPRINT-4M-V2.md` (历史参考)
3. `harness/rules/state/snapshots/DOMAIN_STATUS.md` (领域状态)
4. `harness/engineering/issues-index.md` (issue 镜像)

### 重要约束
- 严格保持 1:1 行为等价 (不要顺手"优化")
- 业务口径不能改 (见 ADR-001~010)
- 灰度默认 OFF (DddRefactorProperties.* 默认 false)
- 每 issue 一个 commit, 多 issue 合并需解释
- Spring 依赖不能进入 policy 层

## 九、修订记录

- **v3** (2026-06-26): 基于实测 LOC + 4M-V2 W1-W3 实际节奏
- **v2** (2026-06-21): 原 Sprint 4M-V2, 70% 目标, 16 周
- **v1** (2026-06-19): Sprint 2M, 70% 目标, 8 周
