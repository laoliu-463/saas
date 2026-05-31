> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# 寄样台看板改造设计

## 概述

将寄样台从表格列表视图改造为看板视图，以状态流转为核心组织寄样单，缩短操作路径，提升业务可视性。

## 现状

- **文件**: `frontend/src/views/sample/index.vue`（~210行）
- **视图**: 标准表格，分页加载
- **状态流转**: PENDING_AUDIT → PENDING_SHIP → SHIPPED → PENDING_TASK → FINISHED / REJECTED
- **问题**: 无法一屏看全在途寄样，操作需定位行 + 找按钮，缺少超时预警

## 设计

### 看板布局

5 列固定看板，每列对应一个活跃状态：

| 列 | 状态值 | 操作按钮 |
|---|---|---|
| 待审核 | PENDING_AUDIT | 通过 / 拒绝 |
| 待发货 | PENDING_SHIP | 填写单号 |
| 快递中 | SHIPPED | 确认签收 |
| 待交作业 | PENDING_TASK | 标记完成 |
| 已完成 | FINISHED | 查看详情 |

已拒绝 (REJECTED) 不单独占列，收纳在页面底部可展开的折叠区域。

列头显示卡片数量 badge，例如"待审核 **3**"。每列顶部支持搜索和筛选。

### 卡片设计

每张卡片 ~120px 高，信息分层：

- **第一行（必显）**: 达人名称 + 商品名称（一行，超长截断）
- **第二行（必显）**: 申请时间（相对时间，如"3天前"）+ 物流信息（仅快递中状态显示）
- **第三行（条件）**: 超时/风险标记（红色标签）
- **底部**: 操作按钮，根据所在列显示对应操作

卡片视觉状态：

- **正常**: 白色背景，浅灰边框
- **即将超时**（剩余 <25% 时长）: 浅黄背景 + 橙色左边框 4px + 时钟图标
- **已超时**: 浅红背景 + 红色左边框 4px + "超时 X天" 红色标签

点击卡片弹出右侧抽屉显示完整详情（商品信息、达人信息、时间线）。

### 超时规则

每个状态定义合理停留时间：

| 状态 | 合理时长 | 超时阈值 |
|---|---|---|
| 待审核 | 24h | >24h 标红 |
| 待发货 | 48h | >48h 标红 |
| 快递中 | 72h | >72h 标红 |
| 待交作业 | 7天 | >7天 标红 |

超时计算基于 `stateEnterTime`（进入当前状态的时间）。前端本地计算，不依赖后端额外接口。

### 交互

- **卡片操作**: 操作按钮直接嵌在卡片底部，点击即执行（拒绝弹出原因输入）
- **批量操作**: 每列顶部 [批量操作] 下拉，支持批量审核通过、批量标记签收
- **搜索**: 顶部全局搜索框实时过滤所有列，每列可展开本列筛选
- **空状态**: 空列显示灰色提示文案
- **已拒绝区**: 页面底部折叠区，点击展开

### 数据流

一次请求获取全量看板数据（不分页），寄样单数量通常在百级。

**API 设计**:

```
GET /samples/board
```

返回按状态分组的数据：

```typescript
interface SampleBoard {
  PENDING_AUDIT: SampleCard[]
  PENDING_SHIP: SampleCard[]
  SHIPPED: SampleCard[]
  PENDING_TASK: SampleCard[]
  FINISHED: SampleCard[]
  REJECTED: SampleCard[]
}

interface SampleCard {
  id: string
  productName: string
  talentName: string
  status: string
  createTime: string
  stateEnterTime: string    // 进入当前状态的时间
  trackingNo?: string
  logisticsCompany?: string
}
```

**状态变更**: 操作成功后前端乐观更新（卡片从旧列移到新列），同时后台请求刷新做一致性校验。现有状态变更接口 `PUT /samples/{id}/status` 不变。

## 改动范围

### 后端

1. 新增 `GET /samples/board` 接口，返回按状态分组的数据
2. `Sample` 实体增加 `stateEnterTime` 字段（或复用 `updateTime`）

### 前端

1. 重写 `frontend/src/views/sample/index.vue` — 表格改看板
2. 新增 `frontend/src/components/SampleCard.vue` — 看板卡片组件
3. 新增 `frontend/src/components/KanbanColumn.vue` — 通用看板列组件（后续可复用到商品库改造）
4. `frontend/src/api/sample.ts` — 新增 `getSampleBoard` 接口

## 验收标准

- [ ] 看板 5 列正确显示各状态的寄样单
- [ ] 卡片上可直接完成审核、发货、签收、完成操作
- [ ] 超时卡片自动标红，即将超时卡片显示黄色警告
- [ ] 全局搜索能跨列过滤
- [ ] 状态变更后卡片自动移动到对应列
- [ ] 已拒绝卡片在底部折叠区可展开查看
- [ ] 空列显示合理空状态文案

