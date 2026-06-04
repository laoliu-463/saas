/**
 * 订单列表筛选条件
 * 包含所有可筛选字段，对应 OrderList.vue 中的筛选面板
 * 字段值为空字符串时表示该筛选项未启用
 */
export interface OrderListFilters {
  orderId: string        // 订单号精确匹配
  status: string | null  // 订单状态（null 表示全部）
  talentId: string       // 达人 ID
  merchantId: string     // 商家 ID
  activityId: string     // 团长活动 ID
  shopName: string       // 店铺名称（模糊匹配）
  productId: string      // 商品 ID
  productName: string    // 商品名称（模糊匹配）
  talentName: string     // 达人名称（模糊匹配）
  colonelName: string    // 团长名称（模糊匹配）
  channelName: string    // 渠道名称（模糊匹配）
  recruitType: string | null // 招募类型（null 表示全部）
}

/**
 * 订单时间字段类型
 * - createTime: 按下单时间筛选（估算口径）
 * - settleTime: 按结算时间筛选（真实口径）
 */
export type OrderTimeField = 'createTime' | 'settleTime'

/** 订单查询基础输入（不含分页） */
interface BaseOrderQueryInput {
  filters: OrderListFilters
  timeField: OrderTimeField
  dateRange: [number, number] | null // 时间范围 [startTs, endTs]，毫秒时间戳
}

/** 订单分页查询输入（含分页参数） */
interface OrderPageQueryInput extends BaseOrderQueryInput {
  page: number
  pageSize: number
}

/**
 * 将毫秒时间戳转为 YYYY-MM-DD 格式字符串
 * @param value 毫秒时间戳
 * @returns 格式化后的日期字符串
 */
function dateToYmd(value: number) {
  const date = new Date(value)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * 解析日期范围参数
 * 将前端时间戳范围转换为后端需要的 startDate / endDate 字符串
 * @param dateRange [startTs, endTs] 毫秒时间戳，null 表示不限时间
 * @returns 包含 startDate 和 endDate 的对象
 */
function resolveDateParams(dateRange: [number, number] | null) {
  if (!dateRange) {
    return {
      startDate: undefined,
      endDate: undefined
    }
  }
  return {
    startDate: dateToYmd(dateRange[0]),
    endDate: dateToYmd(dateRange[1])
  }
}

/**
 * 构建订单导出参数
 * 将筛选条件、时间字段、日期范围组装为后端导出接口所需参数
 * 空字符串字段转为 undefined，避免后端误判为有效筛选值
 * @param input 基础查询输入（不含分页）
 * @returns 后端导出接口参数对象
 */
export function buildOrderExportParams(input: BaseOrderQueryInput) {
  const { filters, timeField, dateRange } = input
  return {
    orderId: filters.orderId || undefined,
    status: filters.status || undefined,
    talentId: filters.talentId || undefined,
    merchantId: filters.merchantId || undefined,
    colonelActivityId: filters.activityId || undefined,
    shopName: filters.shopName || undefined,
    productId: filters.productId || undefined,
    productName: filters.productName || undefined,
    talentName: filters.talentName || undefined,
    colonelName: filters.colonelName || undefined,
    channelName: filters.channelName || undefined,
    recruitType: filters.recruitType || undefined,
    timeField,
    ...resolveDateParams(dateRange)
  }
}

/**
 * 构建订单分页查询参数
 * 在导出参数基础上追加 page 和 size 分页参数
 * @param input 分页查询输入（含筛选条件 + 分页信息）
 * @returns 后端分页接口参数对象
 */
export function buildOrderPageParams(input: OrderPageQueryInput) {
  const { page, pageSize } = input
  return {
    page,
    size: pageSize,
    ...buildOrderExportParams(input)
  }
}

/**
 * 构建订单明细分页查询参数
 * 与 buildOrderPageParams 相同结构，服务于订单明细接口
 * @param input 分页查询输入
 * @returns 后端明细分页接口参数对象
 */
export function buildOrderDetailPageParams(input: OrderPageQueryInput) {
  return buildOrderPageParams(input)
}
