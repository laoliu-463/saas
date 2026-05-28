/**
 * @module api/order
 * @description 订单域 API 模块
 *
 * 负责与后端订单域接口通信，涵盖订单同步、查询、统计和详情等能力。
 * 本模块是前端「归因工作台 — 订单管理」页面的主要数据来源，
 * 同时为数据看板和业绩追踪提供订单基础数据。
 *
 * 核心业务概念：
 * - 归因状态（attributionStatus）：订单是否已完成达人 / 渠道 / 招商归属
 * - 选品来源（pickSource）：订单商品的转链来源标记
 * - 诊断信息（diagnosis）：归因失败时的原因码与建议
 * - 推广匹配（promotion）：订单是否命中推广链接
 * - 寄样匹配（sample）：订单是否关联寄样请求
 */
import request from '../utils/request';

/**
 * 订单详情完整数据结构
 *
 * 采用分组嵌套设计，将订单信息按业务维度划分为商品、渠道、达人、
 * 金额、推广、寄样、诊断、时间等子对象，便于页面按区块渲染。
 *
 * 所有字段均可能为 null，因为订单在同步后可能尚未完成全部关联填充。
 */
export interface OrderDetail {
  orderId: string
  orderStatus?: number | null
  orderStatusText?: string | null
  attributionStatus?: string | null
  attributionStatusText?: string | null
  attributionRemark?: string | null
  pickSource?: string | null
  product?: {
    productId?: string | null
    productName?: string | null
    activityId?: string | null
    activityName?: string | null
    colonelUserId?: string | null
    colonelName?: string | null
  }
  channel?: {
    channelUserId?: string | null
    channelName?: string | null
  }
  talent?: {
    talentId?: string | null
    talentUid?: string | null
    authorId?: string | null
    talentName?: string | null
  }
  amount?: {
    orderAmount?: number | null
    serviceFee?: number | null
    payAmount?: number | null
    settleAmount?: number | null
    estimateServiceFee?: number | null
    effectiveServiceFee?: number | null
    estimateTechServiceFee?: number | null
    effectiveTechServiceFee?: number | null
  }
  promotion?: {
    matched: boolean
    pickSource?: string | null
    promotionUrl?: string | null
    mappingId?: string | null
    createdAt?: string | null
  }
  sample?: {
    matched: boolean
    sampleRequestId?: string | null
    sampleStatus?: string | null
    sampleStatusText?: string | null
    completedByOrderRule?: boolean
  }
  diagnosis?: {
    reasonCode?: string | null
    reasonText?: string | null
    suggestion?: string | null
  }
  time?: {
    createTime?: string | null
    settleTime?: string | null
    syncTime?: string | null
  }
}

/**
 * 同步订单数据
 *
 * 触发后端从抖音开放平台拉取指定时间范围内的订单。
 * 同步是异步过程，此接口仅发起同步请求，不等待完成。
 *
 * @param startTime - 同步起始时间，格式 'YYYY-MM-DD HH:mm:ss'
 * @param endTime - 同步结束时间，格式 'YYYY-MM-DD HH:mm:ss'
 * @param config - axios 请求配置，可用于设置超时等参数
 * @returns 同步结果响应
 */
export function syncOrders(startTime: string, endTime: string, config: any = {}) {
  return request.post('/orders/sync', { startTime, endTime }, config);
}

/**
 * 分页查询订单列表
 *
 * @param params - 查询参数，包含分页、筛选条件（归因状态、商品名、达人等）
 * @param config - axios 请求配置
 * @returns 订单分页列表
 */
export function getOrders(params: any, config: any = {}) {
  return request.get('/orders', { params, ...config });
}

/**
 * 查询未归属订单列表
 *
 * 获取尚未完成达人 / 渠道归属的订单，用于归因工作台的待处理队列。
 *
 * @param params - 查询参数（分页、筛选条件）
 * @returns 未归属订单分页列表
 */
export function getUnattributedOrders(params: any) {
  return request.get('/orders/unattributed', { params });
}

/**
 * 获取订单统计数据
 *
 * 返回订单维度的汇总指标（总数、金额、归因率等），用于看板顶部统计卡片。
 *
 * @param params - 可选的筛选参数
 * @returns 订单统计摘要
 */
export function getOrderStats(params?: any) {
  return request.get('/orders/stats', { params });
}

/**
 * 获取订单筛选器选项
 *
 * 返回下拉筛选器所需的选项列表（如商品名称列表、达人列表等），
 * 用于订单列表页的动态筛选条件。
 *
 * @param params - 可选的上下文参数
 * @returns 筛选选项数据
 */
export function getOrderFilterOptions(params?: any) {
  return request.get('/orders/filter-options', { params });
}

/**
 * 获取订单详情
 *
 * 查询单个订单的完整信息，包括商品、渠道、达人、金额、推广匹配、
 * 寄样匹配、诊断信息和时间线等全部子对象。
 *
 * @param orderId - 订单 ID
 * @returns 订单详情数据
 */
export function getOrderDetail(orderId: string): Promise<OrderDetail> {
  return request.get(`/orders/${orderId}`).then((res: any) => res.data as OrderDetail)
}

/** @deprecated use syncOrders instead */
export function triggerOrderSync() {
  const now = new Date();
  const start = new Date();
  start.setDate(start.getDate() - 30);
  
  const formatDate = (d: Date) => d.toISOString().replace('T', ' ').split('.')[0];
  
  return syncOrders(formatDate(start), formatDate(now));
}
