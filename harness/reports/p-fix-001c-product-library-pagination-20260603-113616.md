# P-FIX-001C 商品库分页弱化改造报告

## 1. 任务概述

- 任务编号：P-FIX-001C
- 任务名称：商品库分页弱化改造
- 执行时间：2026-06-03 11:36
- 环境：本地 `real-pre`
- Selected Gate：Gate 2（Frontend Change）+ Gate 3（Product Domain）
- 是否修改前端：是
- 是否修改后端：否
- 是否修改数据库：否
- 是否重启容器：是，本地 real-pre frontend scope；Compose 实际重建/重启 frontend 和 backend，最终均 healthy
- 是否部署远端：否，用户未要求远端部署

## 2. Harness 读取情况

已读取：`AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`docs/01-V1交付范围与边界.md`、`docs/02-业务闭环总览.md`、`docs/03-领域架构总览.md`、`docs/05-API契约总表.md`、`docs/08-第三方对接总览.md`、`docs/09-测试验收总览.md`、`docs/10-部署运行总览.md`、`docs/验收/real-pre联调手册.md`、`docs/领域/商品域.md`、商品对接与 ADR 文档、`harness/AGENT_CONTRACT.md`、`harness/TASK_ROUTING.md`、`harness/FORBIDDEN_SCOPE.md`、`harness/COMPLETION_GATES.md`、`harness/CURRENT_STATE.md`、`harness/state/DOMAIN_STATUS.md`、`harness/state/KNOWN_ISSUES.md`、`harness/state/DECISIONS.md`、`harness/HARNESS_CHANGELOG.md`、`harness/instructions/product-domain.md`、`harness/skills/product-library.skill.md`、`frontend-ux.skill.md`、`ddd-boundary-check.skill.md`、`ddd-post-task-sync.skill.md`、`real-pre-debug.skill.md`。

说明：任务要求的 `harness/skills/frontend-domain-change.md` 和 `harness/skills/post-task-gc.md` 在当前仓库不存在；已用实际存在的前端/商品域/DDD 同步 skill 兜底。`safety-check -Scope code` 也不被当前脚本接受，实际用 `-Scope frontend` 通过。

## 3. 当前 Git 工作区状态

- 修改前工作区已 dirty。
- 任务前已有 dirty / untracked：U-2.5-B / TEST-1 后端与 SQL、FUNC-001 商品卡片、Harness Gate/报告归档等大量变更。
- 本任务相关变更：`frontend/src/views/product/ProductLibrary.vue`、`frontend/src/views/product/ProductLibrary.test.ts`、本报告、evidence/retro/content-retire 报告，以及商品域状态回写。
- 来源不确定变更：已有大量非本任务 dirty；另有较早生成的 `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` 内容与本轮实际验证不一致，本报告作为本轮最终证据。

## 4. 商品库分页链路现状

- 页面文件：`frontend/src/views/product/ProductLibrary.vue`
- 商品卡片组件：`frontend/src/components/product/ProductSelectionCard.vue`
- 商品库 API 文件：`frontend/src/api/product.ts`，`getProducts(params)` 调用内部 `GET /products`
- 查询参数构造：`frontend/src/views/product/product-filters.ts::buildProductLibraryQueryParams`
- 当前返回结构：页面按 `{ records, total, page, size }` 读取
- 当前是否使用 Naive UI 分页组件：商品库页未使用 `NPagination` / `n-pagination`
- 当前翻页行为：改造后仍用“加载更多”，新页 append 到已加载列表，不替换旧列表
- 当前默认 pageSize：100
- 后端限制：代码证据显示商品库接口 `size` 最大 100；本轮不改后端
- 不需要修改：商品同步、展示规则、数据库、订单、业绩、寄样、达人、权限体系

## 5. 修改方案

选择“加载更多”而非无限滚动。原因：当前页面已有加载更多入口和已加载计数，低风险；无限滚动需要额外防抖、loading 锁和 hover 高度变化处理，本任务无需扩大范围。

保留后端分页，不一次性拉全量。前端默认每页请求 100 条，后端仍控制分页和上限。

## 6. 实际修改清单

| 文件 | 修改 |
| --- | --- |
| `frontend/src/views/product/ProductLibrary.vue` | `PAGE_SIZE` 改为 100；新增 `currentPage`；reset 时清空列表并重置页码/total/hasMore；加载更多按 `currentPage + 1` 请求并 append；筛选和推广状态变更触发第一页重置请求；加载更多增加 loading 锁 |
| `frontend/src/views/product/ProductLibrary.test.ts` | 新增分页弱化单测，覆盖默认 `size=100`、加载更多追加、筛选变更重置第一页 |

## 7. 新交互说明

- 默认加载：首次进入请求 `page=1,size=100`。
- 加载更多：请求下一页 `page=currentPage+1,size=100`，新商品追加到列表。
- 筛选重置：筛选条件或推广状态变更后清空已加载列表，重新请求第一页。
- 搜索/刷新：复用 reset 请求逻辑。
- 已加载全部：`products.length >= total` 时隐藏加载更多并显示“已全部加载”；如果后端无 total，则用返回条数是否小于 pageSize 辅助判断。

## 8. 验证结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Red 测试 | PASS | 新增 `ProductLibrary.test.ts` 后，先因筛选变更未触发第三次 `/products` 请求而失败 |
| 相关前端单测 | PASS | `npm --prefix frontend run test -- src/views/product/ProductLibrary.test.ts src/views/product/product-filters.test.ts src/views/product/components/ProductLibraryFilterPanel.test.ts src/components/product/ProductSelectionCard.test.ts`，4 files / 50 tests passed |
| Typecheck | PASS | `npm --prefix frontend run typecheck` |
| Frontend build | PASS | `npm --prefix frontend run build`；仅 Vite 大 chunk 警告 |
| Lint | SKIP | `frontend/package.json` 不存在 lint 脚本 |
| `git diff --check` | PASS | 无输出，exit code 0 |
| safety-check | PASS | `safety-check.ps1 -Env real-pre -Scope frontend -DryRun` 通过；`Scope=code` 在当前脚本中无效 |
| 容器重启 | PASS | `restart-compose.ps1 -Env real-pre -Scope frontend` 完成，实际 frontend/backend 重建重启 |
| 健康检查 | PASS | `verify-local.ps1 -Env real-pre -Scope frontend`，frontend `statusCode=200`；`docker compose ps` 四服务 healthy |
| 页面 smoke | PASS_WITH_NON_TASK_WARNING | `runtime/qa/out/p-fix-001c-product-library-pagination-smoke.json`：首包 `page=1,size=100,total=1958,records=100`；加载更多 `page=2,size=100`，卡片 100 -> 200；筛选重置 `page=1,size=100` |
| Hover smoke | PASS_WITH_NON_TASK_WARNING | `runtime/qa/out/p-fix-001c-product-library-hover-smoke.json`：商品卡片、复制简介按钮、快速寄样按钮和 drawer 均可见 |

非本任务 warning：Google Fonts 被 real-pre CSP 阻止，已是 `KNOWN_ISSUES.md` 既有问题。复制简介按钮本轮只验证可见，未点击，避免触发真实转链 / `pick_source_mapping` 写入副作用。

## 9. 风险残留

1. 工作区仍有大量任务前 dirty / untracked，不能安全提交或推送。
2. 商品库筛选输入现在会在 `update:filters` 后立即重置请求；如后续希望降低输入框频繁请求，可另起任务增加 debounce。
3. Google Fonts CSP warning 仍存在，不是本任务引入。
4. 未远端部署，远端 real-pre 未验证。

## 10. 回滚方案

仅回滚本任务前端分页文件：

- `frontend/src/views/product/ProductLibrary.vue`
- `frontend/src/views/product/ProductLibrary.test.ts`

不涉及后端、SQL、数据库数据或远端环境。

## 11. Evidence 与最终状态

- Evidence：`harness/reports/evidence-20260603-113632.md`
- Content maintenance：`harness/reports/content-retire-20260603-113617.md`
- Retro：`harness/reports/retro-20260603-113645.md`
- 页面 smoke：`runtime/qa/out/p-fix-001c-product-library-pagination-smoke.json`
- Hover smoke：`runtime/qa/out/p-fix-001c-product-library-hover-smoke.json`
- 最终状态：`PARTIAL`

`PARTIAL` 原因：功能、构建、容器、健康和本地 real-pre 页面 smoke 均通过；但当前仓库存在大量任务前遗留 dirty / untracked，且未安全提交/推送，也未远端部署。
