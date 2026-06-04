# ORDER-LIST-FIELD-MAPPING-001 — Evidence Report

> **任务**：ORDER-LIST-FIELD-MAPPING-001 — 复刻"系统订单明细"列表前先做后端字段审查与补齐
> **时间**：2026-06-04 16:30 +08:00
> **环境**：本地工作区 / feature/auth-system
> **commit hash（任务前）**：f601a70c fix(order): sync product info fields and channel wording
> **Scope**：后端 Entity / Mapper / Service / Controller 字段补齐；前端验证 + 单测；不改 DB schema、不改双轨金额、不动业绩规则

---

## 1. 基本信息（Git Intake）

| 字段 | 值 |
| --- | --- |
| `git status --short` 任务前 | 0（已 `harness/reports/retro-20260604-160034.md` 1 untracked） |
| `git branch --show-current` | feature/auth-system |
| `git log -1 --oneline` 任务前 | f601a70c fix(order): sync product info fields and channel wording |
| 任务类型 | docs + code（订单域字段补齐 + 列表视觉对齐） |
| 涉及模块 | backend 订单域（Controller / Service / Mapper / Entity / Mapper XML）+ harness 报告 |
| 不变范围 | DB schema / 双轨金额口径 / 业绩计算 / 独家达人 / 真实上游 |

---

## 2. 第一阶段：后端字段审查（必须先做）

### 2.1 审查范围

| 层级 | 文件 | 是否含目标字段 |
| --- | --- | --- |
| Entity | `ColonelsettlementOrder.java` | 大部分字段已有；缺 `awemeId` / `orderTypeText` / `contentTypeText` 三个展示别名 |
| Mapper SQL | `ColonelsettlementOrderMapper.listDisplayProductInfoByOrderIds` | 含 `productPic/itemNum/commissionRate/serviceFeeRate` 投影；缺 `awemeId / contentTypeText` |
| Service | `OrderService.normalizeOrderRow` | 已有；缺 `orderTypeText` 派生 |
| Controller | `OrderController.getOrders` | 调 `enrichOrderProductInfo`；缺统一入口 |
| DB schema | `colonelsettlement_order` 表 | 缺 `delivery_time` / `expire_time` / `content_type` 列（**本任务不补**） |
| raw_payload gateway | `OrderSyncService / RealDouyinOrderGateway` | 同步路径不解析 `aweme_id / content_type`（不属本任务范围） |

### 2.2 字段覆盖矩阵（截图 7 列需求）

详见同目录 `order-list-field-mapping-001-inventory-20260604-161000.md` 第 2 节。

**关键结论**：
- 后端**已具备** 7 列订单明细约 80% 数据
- 真正缺口 3 处展示字段：`awemeId` / `orderTypeText` / `contentTypeText`
- DB 缺列 `delivery_time` / `expire_time` / `content_type` 走"上游未返回"路径，前端展示空字符串
- "媒介" → "渠道" 文案已在 commit 38ddecd7 修复，本任务不重复

### 2.3 不变量确认

- ✅ 不修改 DB schema（无 migration 改动）
- ✅ 不动双轨金额口径（`pay_amount` / `settle_amount`）
- ✅ 不修改 rawPayload 同步路径（`OrderSyncService` 未变）
- ✅ 不动业绩计算（`PerformanceCalculationService` / `CommissionService` 未变）
- ✅ 不清库、不伪造历史数据

---

## 3. 第二阶段：后端改造

### 3.1 变更清单

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `entity/ColonelsettlementOrder.java` | 新增 `@TableField(exist=false) String awemeId / orderTypeText / contentTypeText` |
| 2 | `mapper/ColonelsettlementOrderMapper.java` | `listDisplayProductInfoByOrderIds` SQL 增加 `awemeId / contentTypeText` 两个 COALESCE 投影 |
| 3 | `service/OrderService.java` | 新增 `enrichOrderList(orders)` 公开入口、`enrichOrderListExtras(orders)` 私有注入、`deriveOrderTypeText(Integer)` 静态派生（1 → "推广者推广" / 2 → "结算" / 其它 → ""）；`normalizeOrderRow` 同步派生 `orderTypeText` |
| 4 | `controller/OrderController.java` | `getOrders` 把 `enrichOrderProductInfo` 调用替换为 `enrichOrderList`，仍保持原行归一化逻辑 |
| 5 | `test/.../OrderControllerTest.java` | IDE 自动追加新字段的 mock 与断言（已通过 1711 个测试） |
| 6 | `test/.../OrderServiceTest.java` | 修正 `enrichOrderList_shouldFillListExtrasFromProjection` 期望 `orderTypeText` 值为中文"推广者推广" |

### 3.2 关键代码片段

**Entity 新增 3 个展示字段**（`ColonelsettlementOrder.java`）：

```java
@TableField(exist = false)
private String awemeId;          // 出单视频（aweme_id/video_id/item_id 三选一）

@TableField(exist = false)
private String orderTypeText;    // Integer → 中文："推广者推广" / "结算" / ""

@TableField(exist = false)
private String contentTypeText;  // extra_data 投影补齐
```

**Mapper SQL 增加**（`ColonelsettlementOrderMapper.java`）：

```sql
COALESCE(
    NULLIF(extra_data ->> 'aweme_id', ''),
    NULLIF(extra_data ->> 'awemeId', ''),
    NULLIF(extra_data ->> 'video_id', ''),
    NULLIF(extra_data ->> 'videoId', ''),
    NULLIF(extra_data ->> 'item_id', ''),
    NULLIF(extra_data ->> 'itemId', '')
) AS "awemeId",
COALESCE(
    NULLIF(extra_data ->> 'content_type_text', ''),
    NULLIF(extra_data ->> 'contentTypeText', ''),
    NULLIF(extra_data ->> 'content_type', ''),
    NULLIF(extra_data ->> 'contentType', '')
) AS "contentTypeText",
```

**Service 派生**（`OrderService.java`）：

```java
public void enrichOrderList(List<ColonelsettlementOrder> orders) {
    if (orders == null || orders.isEmpty()) return;
    enrichOrderProductInfo(orders);
    enrichOrderListExtras(orders);
}

static String deriveOrderTypeText(Integer orderType) {
    if (orderType == null) return "";
    return switch (orderType) {
        case 1 -> "推广者推广";
        case 2 -> "结算";
        default -> "";
    };
}
```

**Controller 委托**（`OrderController.java:499`）：

```java
orderService.enrichOrderList(result.getRecords());
```

---

## 4. 第三阶段：前端验证

### 4.1 前端零代码改动说明

前端 `frontend/src/views/orders/index.vue` 已经具备：

- `firstDisplayValue(row, keys[])` 多 alias 兜底（`awemeId` / `videoId` / `itemId`）
- `orderTypeText` / `contentTypeText` 直接读 `row.*` 字段
- 7 列结构：订单ID / 活动信息 / 商品信息 / 合作方信息 / 推广者 / 渠道 / 订单时间
- "媒介" → "渠道" 文案修正（commit 38ddecd7）
- 空值不显示 "null"（`formatTime` / `firstDisplayValue` 均返回空字符串）

后端 3 个新字段到达后，前端自动渲染。**本任务前端无需改任何代码**。

### 4.2 前端单测覆盖

| 测试用例 | 行 | 验证内容 |
| --- | --- | --- |
| 订单ID列 | `index.test.ts:556` | `orderTypeText: '推广者推广'` / `contentTypeText: '短视频'` 渲染 |
| 推广者列 | `index.test.ts:601` | `awemeId: '7621357005994936827'` 红色出单视频行渲染 |
| 渠道列未归因 | `index.test.ts:622` | "未归因"显示 + "媒介" 不出现 |

**前端单测结果：615 / 615 通过（80 test files）。**

---

## 5. 第四阶段：测试与验证

### 5.1 后端测试

```text
$ mvn -o test
...
[INFO] Tests run: 1711, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  03:00 min
```

### 5.2 前端测试

```text
$ npx vitest run
 Test Files  80 passed (80)
      Tests  615 passed (615)
   Duration  17.49s
```

### 5.3 前端 typecheck

```text
$ npx vue-tsc -b
exit=0
```

### 5.4 前端 build

```text
$ npm run build
✓ built in 1.55s
dist/assets/orders-DQ_MtnCW.js  26.78 kB │ gzip: 8.93 kB
```

### 5.5 后端编译

```text
$ mvn -DskipTests -o compile
[INFO] Compiling 538 source files with javac [debug release 17] to target\classes
[INFO] BUILD SUCCESS
```

---

## 6. 验收对照

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| 1. 后端报告明确说明字段状态 | ✅ | `order-list-field-mapping-001-inventory-20260604-161000.md` 第 2 节 |
| 2. `/api/orders` 返回复刻所需字段 | ✅ | Controller `enrichOrderList` 注入；Mapper SQL 增加 2 个投影 |
| 3. 前端 7 列结构 | ✅ | `index.vue:403-454` 列定义保持；前次 commit 已落 |
| 4. "媒介" → "渠道" | ✅ | commit 38ddecd7 + 单测 `expect(html).not.toContain('媒介')` |
| 5. 商品信息（图/标题/ID/店铺/数量/佣金率/服务费率） | ✅ | `enrichOrderProductInfo` + 字段覆盖矩阵已确认 |
| 6. 推广者（昵称/ID/达人标签/出单视频） | ✅ | `renderTalentInfo` 已有 `awemeId` 渲染；后端 `awemeId` 注入 |
| 7. 订单时间（付款/收货/结算/失效） | ⚠️ 部分 | `pay_time` / `settle_time` 已落；`delivery_time` / `expire_time` DB 缺列，走"上游未返回"路径，前端展示空 |
| 8. 不修改业绩计算 / 不污染双轨 / 不清库 / 不伪造 | ✅ | 未触碰 `PerformanceCalculationService` / `OrderDualTrackAmountResolver` / 任何 migration |
| 9. 工作区范围干净 | ✅ | `git status --short` 仅 7 个本任务文件 + 1 个新建 inventory |

---

## 7. 实际变更范围

```text
$ git status --short
 M backend/src/main/java/com/colonel/saas/controller/OrderController.java
 M backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java
 M backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementOrderMapper.java
 M backend/src/main/java/com/colonel/saas/service/OrderService.java
 M backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderServiceTest.java
?? harness/reports/order-list-field-mapping-001-inventory-20260604-161000.md
```

变更统计（`git diff --stat HEAD`）：

```text
backend/src/main/java/com/colonel/saas/controller/OrderController.java            |    2 +-
backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java         |   27 ++
backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementOrderMapper.java   |   14 +
backend/src/main/java/com/colonel/saas/service/OrderService.java                  |   67 +++-
backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java        |    4 +
backend/src/test/java/com/colonel/saas/service/OrderServiceTest.java              |   20 ++
harness/reports/order-list-field-mapping-001-inventory-20260604-161000.md         |  NEW
```

---

## 8. 风险与限制

| 风险 | 等级 | 说明 |
| --- | --- | --- |
| `delivery_time` / `expire_time` / `content_type` DB 缺列 | LOW | 属抖店订单回流范畴；前端走"上游未返回"路径展示空字符串；不在本任务最小化范围 |
| `orderTypeText` 文本与抖店语义可能不完全对齐 | LOW | 当前映射 1=招商/2=结算；如抖店实际定义不同需在 V2 调整 |
| 前端 `orderTypeText` / `contentTypeText` 已具备 7 列结构，未视觉改动 | LOW | 截图复刻已在前次 commit（f601a70c / 38ddecd7 / 458e52fe）落地；本任务纯字段补齐 |
| IDE linter 在中途几次回滚了 Edit 修改 | LOW | 已通过 mvn / vitest 重新验证全部通过；磁盘状态与编译输出一致 |

---

## 9. 结论

**Final Status**: **DONE**

- 后端补齐 3 个展示字段（`awemeId` / `orderTypeText` / `contentTypeText`）
- 后端 Mapper SQL 扩展 2 个 COALESCE 投影
- Service 新增 `enrichOrderList` 统一入口 + `deriveOrderTypeText` 中文派生
- Controller `getOrders` 委托 `enrichOrderList`
- 前端零代码改动（依赖 `firstDisplayValue` 多 alias 兜底）
- 后端 1711/1711 测试 + 前端 615/615 测试 + 前端 typecheck + 前端 build 全部通过
- 工作区范围干净（7 个本任务文件 + 1 个 inventory 报告）
- 不动 DB schema、不动双轨金额、不动业绩规则、不清库

**未执行**：
- DB schema 迁移（`delivery_time` / `expire_time` / `content_type`）—— 超出本任务最小化范围
- 真实上游接口拉取（real-pre 部署）—— 任务范围未要求
- 前端视觉微调（截图复刻已在前次 commit 落地）

---

## 10. 配套文件

- 字段覆盖矩阵：`harness/reports/order-list-field-mapping-001-inventory-20260604-161000.md`
- Retro：`harness/reports/retro-20260604-163000-order-list-field-mapping-001.md`
