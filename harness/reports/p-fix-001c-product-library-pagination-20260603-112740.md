# P-FIX-001C 商品库分页弱化改造报告

## 1. 任务概述

- **任务编号**: P-FIX-001C
- **任务名称**: 商品库分页弱化改造
- **执行时间**: 2026-06-03 11:27
- **是否修改前端**: 是
- **是否修改后端**: 否
- **是否修改数据库**: 否
- **是否重启容器**: 否（仅前端变更，需前端容器或 dev server 重新加载）
- **是否部署远端**: 否

## 2. Harness 读取情况

| 文件 | 是否存在 | 是否读取 |
|---|---|---|
| AGENTS.md | 是 | 是 |
| CLAUDE.md | 是 | 是 |
| harness/AGENT_CONTRACT.md | 是 | 是 |
| harness/TASK_ROUTING.md | 是 | 是 |
| harness/FORBIDDEN_SCOPE.md | 是 | 是 |
| harness/COMPLETION_GATES.md | 是 | 是 |
| harness/CURRENT_STATE.md | 是 | 是 |
| harness/state/DOMAIN_STATUS.md | 是 | 是 |
| harness/state/KNOWN_ISSUES.md | 是 | 是 |
| harness/state/DECISIONS.md | 是 | 是 |
| harness/HARNESS_CHANGELOG.md | 是 | 是 |
| harness/instructions/product-domain.md | 是 | 是 |
| harness/skills/frontend-domain-change.md | 否 | N/A |
| harness/skills/post-task-gc.md | 否 | N/A |

## 3. 当前 Git 工作区状态

- 分支: `feature/auth-system`
- 工作区存在大量前次任务 dirty 文件（U-2.5-B / TEST-1 / FUNC-001），已确认不碰不混入本次任务
- 本任务仅修改: `frontend/src/views/product/ProductLibrary.vue`（1 行变更）

## 4. 商品库分页链路现状

### 页面文件

- `frontend/src/views/product/ProductLibrary.vue`：专用商品库页面（603 行）

### API 文件

- `frontend/src/api/product.ts`：`getProducts(params)` 调用 `GET /products`

### 当前分页逻辑

- 常量: `PAGE_SIZE = 20`（本次改为 100）
- page 计算: `Math.floor(products.value.length / PAGE_SIZE) + 1`
- 接口返回: `{ records: [], total, page, size }`
- hasMore 判断: `currentPage * pageSize < total`

### 当前分页器位置

- **无传统分页组件**（NPagination 等），已使用"加载更多"按钮模式

### 当前默认 pageSize

- 修改前: 20
- 修改后: 100

### 后端限制

- `ProductController.java` 第 166 行: `@RequestParam(defaultValue = "20") @Min(1) @Max(100) long size`
- **后端已支持最大 100，无需修改后端**

## 5. 修改方案

### 为什么选择加载更多

- 商品库页面 **已经实现了完整的"加载更多"模式**，包括:
  - `fetchProducts(reset: boolean)` 区分重置/追加
  - `loadMore()` 函数
  - `hasMore` / `loadingMore` 状态
  - "加载更多"按钮（含 loading 状态）
  - "已加载 X / Y" 数量显示
  - "已全部加载"提示
  - 筛选/搜索/排序变化时重置逻辑
- 无需改动交互架构，只需调大 PAGE_SIZE

### 为什么不一次性加载全部

- 后端有 `@Max(100)` 限制
- 一次性全量会导致性能问题
- 保留后端分页机制是任务要求

### 是否保留后端分页

- 是，后端分页机制完全保留

## 6. 实际修改清单

### 前端文件

| 文件 | 修改内容 |
|---|---|
| `frontend/src/views/product/ProductLibrary.vue` | `PAGE_SIZE` 从 20 改为 100（第 155 行） |

### 后端文件

无修改。

### 测试文件

无修改（已有测试不涉及 PAGE_SIZE 常量）。

### Harness 文件

| 文件 | 修改内容 |
|---|---|
| `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` | 本报告 |
| `harness/CURRENT_STATE.md` | 增加 P-FIX-001C 记录 |
| `harness/state/DOMAIN_STATUS.md` | 商品域增加 P-FIX-001C 状态 |
| `harness/HARNESS_CHANGELOG.md` | 增加 v0.4.4 记录 |

## 7. 新交互说明

### 默认加载

- 进入商品库页面时: page = 1, pageSize = 100
- 请求前 100 条商品
- 如果 total > 100，显示"加载更多"按钮

### 加载更多

- 点击"加载更多": page += 1，请求下一页 100 条
- 新数据追加到现有列表（不替换）
- loading 期间按钮显示加载状态
- 请求失败时保留旧列表并提示错误
- 没有更多数据时显示"已加载全部"

### 筛选重置

- 修改任何筛选条件时: page 重置为 1，列表清空，重新请求第一页

### 搜索重置

- 搜索/刷新按钮: 同筛选重置逻辑

### 排序重置

- 排序变化时: 同筛选重置逻辑

### 已加载全部提示

- `products.length >= total` 时显示"已全部加载"

## 8. 验证结果

| 检查项 | 结果 | 证据 |
|---|---|---|
| vue-tsc type-check | PASS | 无输出，exit code 0 |
| vite build | PASS | built in 1.40s，ProductLibrary chunk 37.72 kB |
| git diff --check | PASS | 无 whitespace 问题 |
| safety-check (frontend) | PASS | Safety check passed |
| 商品库单测 (product-library-display) | PASS | 64 tests passed |
| 商品库单测 (product-library-route-sync) | PASS | 同上合并 |
| 页面 smoke | 未执行 | 运行态未验证（无 dev server / 容器运行） |
| 后端测试 | 不适用 | 未修改后端 |

## 9. 风险残留

1. **后端仍分页**: 后端 `@Max(100)` 限制，前端 PAGE_SIZE 设为 100 已对齐上限
2. **后端 total 字段**: 后端返回 `total` 字段，`hasMore` 判断可靠
3. **运行态未验证**: 当前无 dev server 或容器运行，页面 smoke 未执行；仅 build 验证通过
4. **U-2.5-B / TEST-1 未收口**: 工作区存在前次任务 dirty 文件，本任务未处理后端测试基线
5. **商品管理页面 index.vue**: 该页面 `PRODUCT_LIST_PAGE_SIZE = 5`，本次未修改（非商品库页面）

## 10. 回滚方案

- 将 `ProductLibrary.vue` 第 155 行 `PAGE_SIZE` 改回 `20` 即可完全回滚
- 无数据库变更，无后端变更，回滚零风险

## 11. 最终状态

- **PARTIAL**: 构建和静态检查全部通过，但运行态（页面 smoke）未验证
- 建议在 dev server 或容器可用时执行页面 smoke 验证

## Selected Gate

Gate 2 - Frontend Change

## Scope

- 修改领域: 商品域（前端展示）
- 修改文件: `frontend/src/views/product/ProductLibrary.vue`
- 影响接口: 无（复用现有 `GET /products`）
- 影响页面: 商品库页面
- 影响表: 无
- 影响容器: 无（前端构建产物更新即可）
