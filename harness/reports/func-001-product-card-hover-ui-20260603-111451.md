# FUNC-001 商品库商品卡片默认态与悬浮展开态改造

## 1. 任务概述

- 任务编号：FUNC-001
- 任务名称：商品库商品卡片默认态与悬浮展开态改造
- 环境：本地 `real-pre`
- 选择 Gate：Gate 2（Frontend Change）+ Gate 3（Product Domain Change）
- 目标：商品库默认显示紧凑卡片；桌面 hover 时显示“复制简介”“快速寄样”浮层按钮和详细字段抽屉。

## 2. Harness 读取情况

已读取：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/README.md`
- `docs/01-V1交付范围与边界.md`
- `docs/03-领域架构总览.md`
- `docs/05-API契约总表.md`
- `docs/09-测试验收总览.md`
- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/COMPLETION_GATES.md`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/state/DECISIONS.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/instructions/product-domain.md`
- `harness/skills/product-library.skill.md`
- `harness/skills/frontend-ux.skill.md`
- `harness/skills/ddd-boundary-check.skill.md`

说明：请求中列出的 `harness/skills/frontend-domain-change.md` 和 `harness/skills/post-task-gc.md` 在当前仓库不存在，已用实际存在的 `frontend-ux.skill.md` 和 `ddd-post-task-sync.skill.md` 替代。

## 3. 当前基线状态

- 分支：`feature/auth-system`
- 基准 commit：`1ac7796f`
- 修改前工作区：dirty
- 任务前已有 dirty：用户域 U-2.5-B 后端/SQL/测试变更、Harness Completion Gate / Session Exit Gate 相关变更、历史 reports 归档/删除状态等。
- 本任务未回滚任务前已有变更，未把来源不确定变更纳入本任务提交。

## 4. 商品卡片现状与影响分析

- 商品库页面：`frontend/src/views/product/ProductLibrary.vue`
- 商品卡片组件：`frontend/src/components/product/ProductSelectionCard.vue`
- 商品卡片测试：`frontend/src/components/product/ProductSelectionCard.test.ts`
- 商品字段映射：`frontend/src/views/product/product-library-display.ts`
- 商品库 API client：`frontend/src/api/product.ts`
- E2E 用例：`tests/e2e/03b-product-library-drawer-fields.spec.ts`

当前已有逻辑：

- 复制简介：父页面 `copyPromotionLink`，复用转链接口，不改后端。
- 快速寄样：父页面 `openSampleApply`，复用 `QuickSampleModal`，不改寄样状态机。
- 查看详情：父页面 `openDetail`，复用 `ProductDetail`。
- 去百应：组件使用 `card.baiyingUrl` 直接跳转。
- 字段复制：组件内 `copyField()` 使用 `navigator.clipboard`。

最小改造文件清单：

- `frontend/src/components/product/ProductSelectionCard.vue`
- `frontend/src/components/product/ProductSelectionCard.test.ts`
- `tests/e2e/03b-product-library-drawer-fields.spec.ts`

不需要修改：

- 后端 Java
- SQL migration / init SQL
- 商品库 API client
- 订单域、业绩域、寄样状态机、权限系统

## 5. 根因与改造方案

阶段性根因：当前商品库卡片工作区已有默认态/hover 态改造草稿，但仍存在两个可验证缺口：

1. 默认态“公开佣金”把 `-` 当作有效投放期佣金，导致有普通佣金率时仍展示 `佣 -`。
2. hover 详情字段标签和顺序与 FUNC-001 需求不完全一致：`寄样要求 / 推广时间 / 店铺评分` 需对齐为 `寄样 / 时间 / 商家评分`，且 `商家评分` 应排在最后。

修复方案：

- 新增 `displayCommissionRate` computed，把 `campaignCommissionRate === '-'` 视为空占位，回退到 `commissionRate`。
- 调整 hover 详情字段顺序和标签为：招商、寄样、时间、团长、店铺、活动、库存、商家评分。
- 扩展组件单测，先观察失败，再修改实现并回归通过。
- E2E 增加 `E2E_SKIP_TEST_SEED=true` 开关，real-pre 验证时跳过 `/api/test/seed`，不打开 test/mock 开关。

## 6. 实际修改清单

| 文件 | 修改 |
| --- | --- |
| `frontend/src/components/product/ProductSelectionCard.vue` | 增加 `displayCommissionRate`，修正公开佣金兜底；调整 hover 字段标签和顺序 |
| `frontend/src/components/product/ProductSelectionCard.test.ts` | 新增佣金兜底和 FUNC-001 字段顺序测试；更新“商家评分”断言 |
| `tests/e2e/03b-product-library-drawer-fields.spec.ts` | 字段文案改为“商家评分”；增加 real-pre 跳过 test seed 的显式开关 |

## 7. 默认态实现说明

- 卡片默认态保留当前紧凑商品卡结构：图片、标题、ID/链接/刷新/详情、总销量、去百应、核心指标。
- 核心指标第一项优先展示投放期佣金；当投放期佣金为空或 `-` 时回退到普通佣金率。
- 空字段仍按现有前端 fallback 展示，不新增后端接口。

## 8. 悬浮态实现说明

- hover 时图片区浮现“复制简介”“快速寄样”按钮。
- hover 时底部抽屉显示详细字段。
- 字段顺序已对齐：招商、寄样、时间、团长、店铺、活动、库存、商家评分。
- 每行继续保留复制按钮；空字段不触发复制并显示 `-`。

## 9. 字段映射说明

| 需求字段 | 前端字段 |
| --- | --- |
| 商品图 | `card.imageUrl` |
| 商品名 | `card.productName` |
| 商品 ID | `card.productId` |
| 总销量 | `card.totalSalesText` |
| 公开佣金 | `displayCommissionRate` |
| 直播价 | `card.livePrice` |
| 佣金率 | `card.commissionRate` |
| 服务费率 | `card.serviceFeeRate` |
| 招商 | `card.recruiterName` |
| 寄样 | `card.sampleRequirement` |
| 时间 | `card.activityStartTime + card.activityEndTime` |
| 团长 | `card.colonelName` |
| 店铺 | `card.shopName` |
| 活动 | `card.activityName` |
| 库存 | `card.productStock` |
| 商家评分 | `card.shopScore` |

## 10. 交互复用说明

- 复制简介：复用父页面 `copyPromotionLink`，未改转链接口。
- 快速寄样：复用 `QuickSampleModal`，未改寄样后端。
- 查看详情：复用 `ProductDetail`。
- 去百应：复用 `card.baiyingUrl`。
- ID / 链接 / 字段复制：复用组件内 `copyField()`。

## 11. 测试与验证结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Red 测试 | PASS | 新增 2 条断言后，`ProductSelectionCard.test.ts` 先失败：`佣 -` vs `佣 20%`，字段顺序不一致 |
| 组件单测 | PASS | `npm --prefix frontend run test -- src/components/product/ProductSelectionCard.test.ts`，12 tests passed |
| 相关前端单测 | PASS | 5 files / 82 tests passed |
| Typecheck | PASS | `npm --prefix frontend run typecheck` |
| 前端构建 | PASS | `npm --prefix frontend run build` |
| diff check | PASS | `git diff --check` |
| safety-check | PASS | `safety-check.ps1 -Env real-pre -Scope frontend -DryRun` |
| 容器重启 | PASS | `restart-compose.ps1 -Env real-pre -Scope frontend`；实际 frontend/backend 均重建并 healthy |
| 健康检查 | PASS | `verify-local.ps1 -Env real-pre -Scope frontend`，frontend statusCode=200 |
| real-pre E2E | PASS | `E2E_SKIP_TEST_SEED=true npx playwright test tests/e2e/03b-product-library-drawer-fields.spec.ts --project=chromium`，2 passed |
| 页面 smoke | PASS_WITH_NON_TASK_WARNING | hover 后按钮 opacity 0 -> 1，drawer opacity 0 -> 1，drawer maxHeight 360px；截图见 `runtime/qa/out/func-001-product-card-hover-expanded.png` |

非本任务 warning：

- real-pre 页面加载 Google Fonts 被 CSP 阻止，产生 1 个 console error 和 1 个 requestfailed；该问题不是本次商品卡片改造引入，已写入 `KNOWN_ISSUES.md`。

## 12. 是否修改后端

否。未修改后端 Java、后端配置、后端接口。

## 13. 是否修改数据库

否。未修改 SQL migration、初始化脚本或数据库数据。

## 14. 是否重启容器

是。执行 `restart-compose.ps1 -Env real-pre -Scope frontend`。

实际结果：`frontend-real-pre` 和 `backend-real-pre` 均被 Docker Compose 重建/重启，最终 compose ps 显示 healthy。

## 15. 风险残留

1. 工作区仍有大量任务前遗留 dirty / untracked 变更，不能安全执行 `git add -A` 或 `agent-do.ps1` 的自动提交路径。
2. 未创建 commit / 未 push；最终会话状态因此不能写 DONE。
3. Google Fonts 被当前 CSP 阻止，页面 smoke 仅能写 `PASS_WITH_NON_TASK_WARNING`。
4. 未执行远端部署；用户未要求远端部署。

## 16. 回滚方案

仅回滚本任务前端改动：

- `frontend/src/components/product/ProductSelectionCard.vue`
- `frontend/src/components/product/ProductSelectionCard.test.ts`
- `tests/e2e/03b-product-library-drawer-fields.spec.ts`

注意：不要回滚任务前已有 U-2.5-B / Harness 变更。

## 17. Evidence 与最终状态

- Evidence：`harness/reports/evidence-20260603-111733.md`
- Content maintenance：`harness/reports/content-retire-20260603-111343.md`
- Retro：`harness/reports/retro-20260603-111824.md`
- UI 截图：`runtime/qa/out/func-001-product-card-hover-expanded.png`
- 最终状态：`PARTIAL`

`PARTIAL` 原因：功能、构建、容器、健康和 real-pre 页面验证均已通过；但仓库工作区存在大量任务前遗留 dirty 变更，且本轮未能安全提交/推送，未满足 Session Exit Gate 的 clean-state / git 收口要求。
