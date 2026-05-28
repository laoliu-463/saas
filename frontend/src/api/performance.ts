/**
 * @module api/performance
 * @description 业绩追踪 API 模块
 *
 * 负责业绩域的数据查询、汇总和管理，核心能力包括：
 * - 单笔 / 批量业绩查询
 * - 业绩列表分页查询（支持多维筛选）
 * - 业绩汇总统计（预估 / 有效双轨）
 * - 业绩数据导出（Excel）
 * - 月度业绩重算
 * - 独家商家管理
 *
 * 业务概念：
 * - 预估轨（estimate）：基于订单支付金额的实时估算
 * - 有效轨（effective）：基于结算金额的最终确认
 * - 服务费利润 = 服务费收入 - 技术服务费 - 渠道佣金 - 招商佣金
 * - 所有金额单位为分（cent），展示时需通过 centToYuan 转换为元
 */
import request from '../utils/request';

/**
 * 业绩追踪汇总指标
 *
 * 包含订单量、金额和各级费用/利润的完整财务指标集。
 * 用于业绩看板的统计卡片和图表数据。
 */
export interface PerformanceTrackSummary {
  orderCount: number;
  orderAmount: number;
  serviceFeeIncome: number;
  techServiceFee: number;
  serviceFeeProfit: number;
  serviceFeeExpense: number;
  recruiterCommission: number;
  channelCommission: number;
  grossProfit: number;
}

/**
 * 业绩汇总双轨数据
 *
 * 包含预估轨和有效轨两套完整的汇总指标，
 * 用于对比展示预估值与最终确认值的差异。
 */
export interface PerformanceSummary {
  /** 预估轨汇总指标（基于支付金额实时估算） */
  estimate: PerformanceTrackSummary;
  /** 有效轨汇总指标（基于结算金额最终确认） */
  effective: PerformanceTrackSummary;
}

/**
 * 业绩明细记录
 *
 * 每条记录对应一笔订单的业绩计算结果，包含归属关系（商品、活动、
 * 合作伙伴、达人、渠道、招商）和双轨金额指标。
 * 金额单位均为分（cent）。
 */
export interface PerformanceDetail {
  orderId: string;
  productId?: string;
  productName?: string;
  activityId?: string;
  activityName?: string;
  partnerId?: string;
  partnerName?: string;
  talentId?: string;
  talentName?: string;
  finalChannelId?: string;
  finalChannelName?: string;
  finalRecruiterId?: string;
  finalRecruiterName?: string;
  payAmount?: number;
  settleAmount?: number;
  estimateServiceProfit?: number;
  effectiveServiceProfit?: number;
  estimateRecruiterCommission?: number;
  effectiveRecruiterCommission?: number;
  estimateChannelCommission?: number;
  effectiveChannelCommission?: number;
  estimateGrossProfit?: number;
  effectiveGrossProfit?: number;
  orderStatus?: string;
  payTime?: string;
  settleTime?: string;
  calculatedAt?: string;
}

/**
 * 业绩批量查询请求体
 */
export interface PerformanceBatchPayload {
  /** 订单 ID 列表 */
  orderIds: string[];
}

/**
 * 月度业绩重算请求体
 *
 * 触发指定月份的业绩数据重新计算，用于修正数据错误或补充新规则。
 */
export interface PerformanceRecalculateMonthPayload {
  /** 目标月份，格式 'YYYY-MM' */
  month: string;
  /** 重算原因说明（用于审计日志） */
  reason: string;
}

/**
 * 业绩列表查询参数
 *
 * 支持按订单、商品、合作伙伴、活动、达人、渠道、招商等多维筛选，
 * 以及时间范围、金额轨道和分页排序控制。
 */
export interface PerformanceListParams {
  orderId?: string;
  productId?: string;
  productName?: string;
  partnerId?: number;
  partnerName?: string;
  activityId?: string;
  talentId?: string;
  channelId?: string;
  recruiterId?: string;
  orderStatus?: string;
  timeFilterType?: 'pay' | 'settle' | 'calculate';
  timeStart?: string;
  timeEnd?: string;
  startDate?: string;
  endDate?: string;
  amountTrack?: 'estimate' | 'effective' | 'both';
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: string;
}

/**
 * 查询单笔订单的业绩详情
 *
 * @param orderId - 订单 ID
 * @returns 业绩明细数据
 */
export const getPerformance = (orderId: string) => request.get(`/performance/${orderId}`);

/**
 * 批量查询订单业绩数据
 *
 * @param orderIds - 订单 ID 列表
 * @returns 批量业绩明细列表
 */
export const batchGetPerformance = (orderIds: string[]) =>
  request.post('/performance/batch', { orderIds } satisfies PerformanceBatchPayload);

/**
 * 分页查询业绩列表
 *
 * @param params - 查询参数（多维筛选、分页、排序）
 * @returns 业绩分页列表
 */
export const listPerformance = (params?: PerformanceListParams) => request.get('/performance', { params });

/**
 * 获取业绩汇总统计
 *
 * 返回预估轨和有效轨的汇总指标，用于业绩看板顶部统计。
 *
 * @param params - 筛选条件
 * @returns 双轨业绩汇总数据
 */
export const getPerformanceSummary = (params?: PerformanceListParams) =>
  request.get('/performance/summary', { params });

/**
 * 导出业绩数据
 *
 * 以 blob 响应下载 Excel 文件，包含筛选条件匹配的所有业绩明细。
 *
 * @param params - 筛选条件（与 listPerformance 相同）
 * @returns Blob 数据，前端创建下载链接
 */
export const exportPerformance = (params?: PerformanceListParams) =>
  request.get('/performance/export', { params, responseType: 'blob' });

/**
 * 查询独家商家详情
 *
 * @param partnerId - 合作伙伴 ID
 * @returns 独家商家信息（含合作状态、有效期等）
 */
export const getExclusiveMerchant = (partnerId: string) => request.get(`/exclusive-merchants/${partnerId}`);

/**
 * 查询当前用户的独家商家列表
 *
 * @returns 当前用户负责的独家商家列表
 */
export const getMyExclusiveMerchants = () => request.get('/exclusive-merchants/my');

/**
 * 触发月度业绩重算
 *
 * 对指定月份的所有业绩记录进行重新计算，用于修正规则变更后的数据。
 * 操作会写入审计日志。
 *
 * @param data - 重算请求体（月份 + 原因）
 * @returns 重算任务结果
 */
export const recalculatePerformanceMonth = (data: PerformanceRecalculateMonthPayload) =>
  request.post('/performance/recalculate-month', data);

/**
 * 分转元工具函数
 *
 * 将后端返回的分（cent）金额转换为元（yuan）字符串，
 * 保留两位小数。null / undefined 视为 0。
 *
 * @param cent - 分金额值
 * @returns 格式化后的元金额字符串（如 "12.34"）
 */
export const centToYuan = (cent?: number | null): string => {
  const value = cent == null ? 0 : cent;
  return (value / 100).toFixed(2);
};
