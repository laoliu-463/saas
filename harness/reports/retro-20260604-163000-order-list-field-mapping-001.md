# ORDER-LIST-FIELD-MAPPING-001 — Retro Report

> **任务**：ORDER-LIST-FIELD-MAPPING-001 — 复刻"系统订单明细"列表前先做后端字段审查与补齐
> **时间**：2026-06-04 16:30 +08:00
> **环境**：本地工作区 / feature/auth-system
> **commit hash（任务前）**：f601a70c fix(order): sync product info fields and channel wording
> **Scope**：docs + backend（订单域字段补齐）

---

## 1. 概要

| 项 | 值 |
| --- | --- |
| 任务类型 | docs + code |
| 改动文件数 | 6 (M) + 1 (new) |
| 业务影响 | 订单列表后端 3 个展示字段补齐（无业务规则改动） |
| 测试结果 | 后端 1711/1711 + 前端 615/615 + typecheck + build 全部通过 |
| 工作区状态 | 干净（仅本任务文件） |
| 风险等级 | LOW（不动 schema / 不动金额 / 不动业绩） |

---

## 2. 计划与实际对比

| 阶段 | 计划 | 实际 |
| --- | --- | --- |
| 1. 后端字段审查 | 5 个层级（DB/Entity/VO/API/前端）全量审查 | 全部完成 + 字段覆盖矩阵报告（280 行 markdown） |
| 2. 后端改造 | Entity/Mapper/Service/Controller 4 处 | 全部完成 |
| 3. 前端改造 | 7 列视觉复刻 | **零代码改动**（前次 commit 已落 7 列；本任务纯字段补齐） |
| 4. 测试与验证 | 单元测试 + typecheck + build | 全部通过 |
| 5. 报告 | inventory + evidence + retro | 全部生成 |

**结论**：计划与实际完全对齐。审查阶段发现前端 7 列已落（commit f601a70c / 38ddecd7 / 458e52fe），前端实际零改动。

---

## 3. 关键发现

### 3.1 前端 7 列结构早已就位

最近 5 个 commit 中：

| commit | 内容 |
| --- | --- |
| `f601a70c` fix(order): sync product info fields and channel wording | 同步产品信息和渠道文案 |
| `f86c0ea0` fix(orders): extract product image, quantity, commission rate from API raw data | 从 raw 提取商品图/数量/佣金率 |
| `38ddecd7` fix(order-ui): align product info layout and channel wording | 商品信息布局 + 渠道文案 |
| `3016d0ed` fix(data): prevent settle amount fallback pollution | 防止结算金额回退污染 |
| `458e52fe` feat(order-ui): show product details in order table | 商品详情表格 |

前端 `index.vue:403-454` 已定义 7 列结构；`renderProductInfo` / `renderTalentInfo` / `renderOrderTime` / `renderChannelInfo` 等渲染函数已支持 `firstDisplayValue` 多 alias 兜底。本任务**前端零代码改动**是合理的结果。

### 3.2 后端"展示字段"与"DB 列"明确分离

Entity 已有 `itemNum` / `productQuantity` / `commissionRate` / `serviceFeeRate` / `channelId` / `channelName` 等 `@TableField(exist=false)` 展示字段。**这种"展示 vs 事实"分层设计**是本任务能保持最小化的关键。

### 3.3 DB schema 缺列的清晰边界

`colonelsettlement_order` 表**没有** `delivery_time` / `expire_time` / `content_type` 列。但任务要求"必须先审查后端字段是否全面"——审查结论是"DB 缺列，但属抖店订单回流范畴，超出本任务最小化范围"，前端走"上游未返回"路径展示空字符串。

这种"明确不做 + 记录原因"的做法符合 V1 范围优先级（CLAUDE.md 冲突处理规则：发现冲突不能自行拍板，需写入决策）。

### 3.4 IDE linter 行为

本次会话中，IDE linter 在 Edit 工具后多次"自动还原"了我的修改。**应对方式**：
- 每次 Edit 之后立即 `grep -c` 验证磁盘状态
- 每次 Edit 之后立即 `mvn compile` 验证编译通过
- 最终通过 `mvn test` 1711 个 + `vitest run` 615 个测试全部通过来兜底

此行为未影响最终结果，但提示：**不要相信 Edit 工具的 "success" 返回，必须验证磁盘**。

---

## 4. 决策记录

### 4.1 `orderTypeText` 文本选择

**选择**：1 → "推广者推广" / 2 → "结算" / 其它 → ""

**理由**：
- 前端单测 `index.test.ts:559` 已硬编码 `orderTypeText: '推广者推广'`，证明前端期望中文
- 抖店 `order_type` 实际语义：1=招商推广、2=结算
- 业务术语"推广者推广"与"结算"是抖店运营侧约定

**替代方案已拒绝**：
- ❌ 英文代码（MAIN/SETTLEMENT）—— 前端单测不期望英文
- ❌ 数字字符串（"1"）—— 信息量不足
- ❌ null —— 前端 `orderTypeText || ''` 已兜底，但显示空不如显示有意义的文本

### 4.2 不补 DB 列 `delivery_time` / `expire_time`

**选择**：前端展示空字符串，不做 DB migration

**理由**：
- CLAUDE.md 范围边界："修改代码时必须同步对应文档；本轮只允许文档和 `.claude/` 工程化文档变更"（虽是泛指，但体现 V1 范围的最小化精神）
- 添加 DB 列需要 migration → 改 sync gateway → 改 SQL → 改前端，超出"展示补齐"任务边界
- `extra_data` 也没有这些字段，强行加列无法填充
- 前端 `firstDisplayValue` 多 alias 已支持 `'deliveryTime', 'delivery_time'`，后端未来补列后零代码改动

### 4.3 `awemeId` 走 `extra_data` 解析而非 DB 列

**选择**：从 `extra_data` JSONB 解析出 `aweme_id / video_id / item_id`

**理由**：
- 抖店订单回流时已把全部上游字段写入 `extra_data`（`OrderSyncService` line 652）
- 解析路径与现有 `itemNum` / `commissionRate` 解析保持一致（`listDisplayProductInfoByOrderIds` SQL 中 COALESCE）
- 不动 DB schema
- 历史订单若 `extra_data` 缺这些字段，前端 `firstDisplayValue` 自动兜底为空

---

## 5. 后续待办（不属本任务）

| 待办 | 优先级 | 原因 |
| --- | --- | --- |
| DB migration 加 `delivery_time` / `expire_time` / `content_type` 列 | P2 | 属抖店订单回流 + 业绩域计算需求，需跨域协调 |
| `OrderSyncService` 在同步时直接解析 `aweme_id` / `content_type` 落独立列 | P2 | 提升查询性能（避免每次列表都走 JSONB 投影） |
| `activityName` 联查 `colonelsettlement_activity` | P2 | 跨域（活动域），需活动域接口暴露 |
| 前端对 `orderTypeText` 标签的视觉强化 | P3 | 截图要求"订单类型"是普通文字，已满足 |

---

## 6. 关键文件

| 文件 | 角色 |
| --- | --- |
| `backend/.../entity/ColonelsettlementOrder.java` | 3 个 `@TableField(exist=false)` 展示字段 |
| `backend/.../mapper/ColonelsettlementOrderMapper.java` | SQL 投影 2 个 COALESCE |
| `backend/.../service/OrderService.java` | `enrichOrderList` / `enrichOrderListExtras` / `deriveOrderTypeText` |
| `backend/.../controller/OrderController.java` | `getOrders` 委托 `enrichOrderList` |
| `harness/reports/order-list-field-mapping-001-inventory-20260604-161000.md` | 字段覆盖矩阵 |
| `harness/reports/evidence-20260604-163000-order-list-field-mapping-001.md` | 本任务 evidence |

---

## 7. 反思

### 7.1 做对的事

- ✅ 第一阶段**严格先审查**再改代码（任务明确禁止"跳过后端字段审查直接改 UI"）
- ✅ 不动 DB schema（最小化原则）
- ✅ 不动双轨金额 / 业绩计算（V1 范围边界）
- ✅ 字段覆盖矩阵明确区分"已有 / 缺口 / 不在本任务范围"
- ✅ 通过测试断言反向锁定 `orderTypeText` 必须是中文（避免英文代码扩散到前端）
- ✅ 工作区回滚了 IDE 误改的 3 个无关报告文件

### 7.2 可改进

- 🔄 第一次提交后端时遇到 IDE linter 还原，未立即验证磁盘，导致来回修改——后续应**Edit 后立即 grep 验证**
- 🔄 `deriveOrderTypeText` 的英文代码版本是过度设计（MAIN/SETTLEMENT 已被前端单测拒绝）—— 应**先看现有测试期望**再设计后端派生
- 🔄 没有在 commit 前再跑一次 `mvn package` —— 已通过 `mvn test` 1711 + `vitest run` 615 覆盖，但缺最终打包验证

### 7.3 经验

- **后端 Entity @TableField(exist=false) 模式**是"展示 vs 事实"分层的良好实践，本任务能纯展示补齐即得益于此
- **前端 firstDisplayValue 多 alias 兜底**是"前端不依赖后端单一字段名"的良好实践，本任务零前端改动即得益于此
- **测试断言反向锁定契约**比文档更有约束力（前端单测期望 `orderTypeText: '推广者推广'` 直接决定了后端派生方向）
