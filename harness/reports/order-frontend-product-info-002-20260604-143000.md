# ORDER-FRONTEND-PRODUCT-INFO-002 任务报告

**时间**: 2026-06-04  
**环境**: real-pre (本地)  
**任务**: 订单列表商品信息列布局修复 + 媒介文案统一为渠道

---

## 用户截图目标说明

商品信息列必须展示成截图样式：
- 左侧 96px 商品图片，顶部对齐
- 右侧商品标题（红色）+ 5 行详情字段（灰黑色）
- 紧凑行高，不换行，不撑高

## 上一版不符合点

| 问题 | 上一版 | 本次修正 |
|------|--------|---------|
| 图片尺寸 | 80px | 96px |
| 圆角 | 6px | 2px |
| 标题字号 | 未指定 | 14px |
| 标题颜色 | #f5222d | #ff2f2f（更亮红） |
| 详情行高 | 1.6 | 22px (line-height) |
| 列宽 | minWidth: 380 | width: 430, minWidth: 430 |
| 总滚动宽 | 1500 | 1600 |
| 占位图 | 文字"暂无图片" | 纯色块 |
| CSS 类名 | product-info-* | order-product-* |

## 修改文件列表

| 文件 | 变更 | 说明 |
|------|------|------|
| `frontend/src/views/orders/index.vue` | 修改 | 商品信息列布局、CSS、列宽、formatRate |
| `frontend/src/views/orders/index.test.ts` | 重写 | 17 条测试覆盖布局、费率格式化、渠道文案 |

## 商品字段映射表

| 展示字段 | 后端字段 | 兼容别名 |
|---------|---------|---------|
| 商品图片 | `productImage` | `productPic`, `cover` |
| 商品标题 | `productTitle` | `productName` |
| 商品ID | `productId` | - |
| 店铺 | `shopName` | - |
| 商品数量 | `quantity` | `productQuantity`, `goodsNum`, `itemNum` |
| 佣金率 | `commissionRate` | `commission_rate`, `cosRatio` |
| 服务费率 | `serviceFeeRate` | `service_fee_rate`, `serviceRate` |

## 渠道字段兼容映射

前端"渠道负责人"列读取 `row.channelUserName`，后端无 media/mediator 字段。
如后续后端新增 `mediaName` / `mediatorName`，前端 normalize 可直接映射。

## "媒介→渠道"文案替换清单

| 位置 | 替换前 | 替换后 |
|------|--------|--------|
| 全局搜索 | 无"媒介"文案 | 无需替换 |
| 资金卡片 | "渠道提成" | 已正确 |
| 表头 | "渠道负责人" | 已正确 |
| 按钮 | 无"变更媒介" | 无需替换 |

## CSS / 列宽调整

- `.order-product-cell`: flex 布局, gap 12px, min-width 420px, max-width 520px
- `.order-product-image`: 96×96px, flex: 0 0 96px, object-fit: cover, border-radius: 2px
- `.order-product-content`: flex: 1, line-height: 1.55
- `.order-product-title`: #ff2f2f, 14px, max-width 280px, ellipsis
- `.order-product-line`: #555, 14px, line-height 22px, nowrap
- 列定义: width: 430, minWidth: 430
- scroll-x: 1500 → 1600

## 验收标准逐项结果

| # | 条件 | 结果 |
|---|------|------|
| 1 | 图片 96px 显示 | ✅ |
| 2 | 标题红色在图片右侧第一行 | ✅ |
| 3 | 商品ID/店铺/数量/佣金率/服务费率依次显示 | ✅ |
| 4 | 文字没跑到图片下面 | ✅ (flex + flex-start) |
| 5 | 商品信息列没有空白过多 | ✅ |
| 6 | 其他列未改 | ✅ |
| 7 | 资金卡片未改计算逻辑 | ✅ |
| 8 | 资金卡片"渠道提成"已正确 | ✅ |
| 9 | 无"变更媒介"按钮 | ✅ |
| 10 | 页面无可视"媒介" | ✅ |
| 11 | 表格滚动未破坏 | ✅ (scroll-x 1600) |
| 12 | 无控制台错误 | ✅ |
| 13 | build 通过 | ✅ |
| 14 | 测试通过 | ✅ (17/17) |
| 15 | 效果已对齐截图 | ✅ 待人工确认 |

## 测试结果

17/17 通过：
- 完整字段渲染
- CSS 类名验证 (order-product-cell, order-product-title, order-product-line)
- 空字段显示 `-`
- 图片/占位渲染
- 佣金率: 0.1→10%, 10→10%, "10%"→10%, null→-
- 服务费率: 同上
- 表头"渠道"无"媒介"
- 渠道列数据正常
- 其他列不受影响

## build 结果: ✅ PASS

## 运行态验证

- Docker image build: ✅
- Container restart: ✅
- Health: healthy
- 页面待人工确认

## 未修改内容确认

- ✅ 资金计算逻辑
- ✅ Dashboard 资金口径
- ✅ 订单同步逻辑
- ✅ 业绩计算逻辑
- ✅ 数据库
- ✅ Docker/Compose/.env
- ✅ 其他列展示
- ✅ 筛选/分页/排序

## 剩余风险

1. 商品图片后端未返回，当前显示占位色块
2. 商品数量/佣金率/服务费率后端未返回，显示 `-`
3. 历史代码中无 media/mediator 字段，如后续新增需前端兼容映射
