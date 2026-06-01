# PLAN-002 复制简介降级弹窗

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。
> 状态：**已实施待验收**（前端兜底链路，需以 `npm run test`、`npm run build` 和浏览器验证收口）。

---

## 一、背景

- [V1 必做] 商品域负责转链并落 `pick_source_mapping`，前端只能使用内部 API 返回的推广链接，不允许在转链失败时复制原始商品链接冒充真实推广链路。
- [V1 必做] real-pre 可能运行在 HTTP 或受限浏览器环境下，`navigator.clipboard.writeText` 可能失败。
- [V1 必做] 复制简介的用户目标不是“浏览器自动复制一定成功”，而是“用户一定能拿到已生成内容”。因此自动复制失败后必须有可见、可全选的手动复制兜底。

---

## 二、问题

[V1 必做] 旧链路把“简介生成成功”和“剪贴板写入成功”混在父级页面里：

1. `product-copy.ts` 的 `writeText` 注入返回 `Promise<void>`，父级无法从结果对象判断是否复制成功。
2. HTTP / 浏览器权限受限时只给 toast，用户拿不到完整简介。
3. 转链失败、缺 `pick_source`、剪贴板失败三个场景没有统一展示证据和操作入口。

---

## 三、推荐路径

[V1 必做] 采用“三层兜底”：

1. 首选 `navigator.clipboard.writeText`。
2. 失败后走隐藏 textarea + `document.execCommand('copy')`。
3. 两条自动复制路径都失败，或转链失败 / 缺 `pick_source` 时，打开 `ManualCopyDialog`，展示完整内容、推广链接、`pick_source`、归因警告和百应入口。

---

## 四、改动清单

| # | 类型 | 路径 | 改动 |
|---|---|---|---|
| 1 | 新增 | `frontend/src/utils/clipboard.ts` | 新增 `tryCopyText(text): Promise<boolean>`，封装 clipboard + execCommand 双路径 |
| 2 | 新增 | `frontend/src/utils/clipboard.test.ts` | 覆盖 clipboard 成功、clipboard 失败 execCommand 成功、双失败 |
| 3 | 新增 | `frontend/src/utils/extractPickSource.ts` | 新增 `extractPickSourceFromUrl` |
| 4 | 新增 | `frontend/src/utils/extractPickSource.test.ts` | 覆盖合法 URL、无 query、非法 URL |
| 5 | 新增 | `frontend/src/components/common/ManualCopyDialog.vue` | 新增手动复制弹窗：只读 textarea、全选、重试、关闭、百应入口 |
| 6 | 新增 | `frontend/src/components/common/ManualCopyDialog.test.ts` | 覆盖渲染、warning、百应入口、retry/close 事件 |
| 7 | 新增 | `frontend/src/views/product/manual-copy.ts` | 集中判断三类降级场景 |
| 8 | 新增 | `frontend/src/views/product/manual-copy.test.ts` | 覆盖不弹窗、剪贴板失败、转链失败、缺 `pick_source` |
| 9 | 修改 | `frontend/src/views/product/product-copy.ts` | `writeText` 改为返回 boolean；结果新增 `copied` |
| 10 | 修改 | `frontend/src/views/product/product-copy.test.ts` | 更新 mock 并覆盖 `copied=false` 场景 |
| 11 | 修改 | `frontend/src/views/product/ProductLibrary.vue` | 接入 `tryCopyText` + `ManualCopyDialog` |
| 12 | 修改 | `frontend/src/views/product/ProductDetail.vue` | 接入 `tryCopyText` + `ManualCopyDialog` |
| 13 | 修改 | `frontend/src/views/product/index.vue` | 接入 `tryCopyText` + `ManualCopyDialog` |

---

## 五、关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 弹窗组件 | `n-modal` | 项目已使用 Naive UI，保持现有组件体系 |
| 挂载位置 | 共享组件，三处父级局部挂载 | 现有 `App.vue` 只挂 provider；商品业务弹窗均由页面局部管理。复制弹窗需要当前 row、文案、重试回调和百应入口，放全局会引入一次性业务状态和回调闭包 |
| 自动复制返回值 | `Promise<boolean>` | 父级必须区分“文案生成成功”和“写剪贴板成功” |
| 缺 `pick_source` | 弹窗警告 | 推广链接可复制不等于归因可确认；缺失时显式提示用户核对 |
| 转链失败 | 弹窗 + 百应入口 | 不复制原始商品链接；提供人工生成入口 |

---

## 六、验收矩阵

| 场景 | 预期 |
|---|---|
| 后端转链成功且 clipboard 成功 | toast 成功，不弹窗 |
| 后端转链成功但 clipboard / execCommand 都失败 | 弹窗展示完整简介，简介包含真实 promotionLink |
| 弹窗点击全选内容 | textarea 内容被选中 |
| 弹窗点击再次尝试复制成功 | toast 成功并关闭弹窗 |
| 弹窗点击再次尝试复制失败 | 弹窗保留并提示手动复制 |
| promotionLink 含 `pick_source` | 弹窗展示 `pick_source` |
| promotionLink 缺 `pick_source` | 弹窗展示“无法确认归因：链接缺少 pick_source” |
| 后端转链失败 | 不复制原始链接，弹窗提供百应入口 |
| HTTP real-pre 剪贴板受限 | 用户仍可从弹窗 textarea 手动复制 |

---

## 七、验证命令

```bash
cd frontend
npm run test -- src/utils/clipboard.test.ts src/utils/extractPickSource.test.ts src/views/product/product-copy.test.ts src/views/product/manual-copy.test.ts src/components/common/ManualCopyDialog.test.ts
npm run test
npm run build
```

---

## 八、阶段性结论

[V1 必做] 本方案修复的是前端复制兜底链路，不改变后端转链规则、不改变 `pick_source` 生成规则、不替代 real-pre 上游验收。

[V1 必做] 只有当单元测试、构建和浏览器 real-pre 场景验证均通过后，才能把状态从“已实施待验收”改为“已验收”。
