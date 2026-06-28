# 商品库下拉布局排查证据

## 结论
PARTIAL

## 证据
- **文件**:
  - `frontend/src/views/product/ProductLibrary.vue`
  - `frontend/src/components/product/ProductSelectionCard.vue`
  - `frontend/src/views/product/components/ProductCard.vue`
  - `frontend/src/views/product/components/ProductLibraryFilterPanel.vue`
- **命令**:
  - `Get-Content` 读取商品库页面、卡片抽屉、筛选组件和前端变更 runbook。
  - `rg -n "selection-card|product-grid|drawer|hover-mode|expanded|grid-template-columns"` 定位布局规则。
  - Playwright 本地布局测量，Vite 端口 `5183`，拦截 `/api/products` 与 `/api/products/categories`。
- **浏览器测量**:
  - 证据目录：`runtime/qa/out/product-grid-layout-investigation-20260628/`
  - 1280/1366/1440/1536 桌面 hover 前后第一行均为 4 个商品。
  - 1280/1366/1440 touch 模式展开前后第一行均为 4 个商品。
  - 1366 桌面：grid 宽 `1054px`，列宽 `251.5px * 4`，卡片固定宽 `252px`。
  - 1280 桌面：grid 宽 `968px`，列宽 `230px * 4`，卡片固定宽 `252px`。
- **图谱**:
  - code-review-graph 统计可用：1707 files / 13769 nodes / 156826 edges。
  - 语义嵌入为 0，源码定位回退到 `rg`。
  - 影响半径探测显示商品库卡片相关文件影响前端商品 API、活动商品 API 和 22 个关联文件。

## 风险
- 当前没有在真实 real-pre 数据和用户实际浏览器上复现“下拉后 4 列变 3 列”。
- `ProductSelectionCard` 抽屉本身是 `position:absolute`，浏览器测量未发现它改变 grid 列数。
- 阶段性根因更可能是父级 grid 的断点按 viewport 判断，而实际内容区被侧边栏压缩：1280 视口下内容区不足以稳定容纳 4 张 252px 卡片。
- 还未执行代码修复、前端构建、容器重启、健康检查和 real-pre 页面验证。

## 下一步
- 修复方向应优先调整 `ProductLibrary.vue` 的 `.product-grid` 与 `ProductSelectionCard.vue` 的固定宽度约束，使列数按容器实际宽度稳定计算。
- 建议新增 Playwright 或组件级视觉回归：hover/click 展开前后 `firstRowCount` 不变化。
- 若进入代码修复，必须执行 `agent-do.ps1 -Env real-pre -Scope frontend` 并补页面截图证据。

## Retro Summary
- 本次无需 Harness 升级。
- 发现报告目录已达 50 文件边界，后续任务前应先归档旧 reports。
