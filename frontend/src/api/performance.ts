import request from '../utils/request';

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

export interface PerformanceSummary {
  estimate: PerformanceTrackSummary;
  effective: PerformanceTrackSummary;
}

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

export interface PerformanceBatchPayload {
  orderIds: string[];
}

export interface PerformanceRecalculateMonthPayload {
  month: string;
  reason: string;
}

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

export const getPerformance = (orderId: string) => request.get(`/performance/${orderId}`);

export const batchGetPerformance = (orderIds: string[]) =>
  request.post('/performance/batch', { orderIds } satisfies PerformanceBatchPayload);

export const listPerformance = (params?: PerformanceListParams) => request.get('/performance', { params });

export const getPerformanceSummary = (params?: PerformanceListParams) =>
  request.get('/performance/summary', { params });

export const exportPerformance = (params?: PerformanceListParams) =>
  request.get('/performance/export', { params, responseType: 'blob' });

export const getExclusiveMerchant = (partnerId: string) => request.get(`/exclusive-merchants/${partnerId}`);

export const getMyExclusiveMerchants = () => request.get('/exclusive-merchants/my');

export const recalculatePerformanceMonth = (data: PerformanceRecalculateMonthPayload) =>
  request.post('/performance/recalculate-month', data);

export const centToYuan = (cent?: number | null): string => {
  const value = cent == null ? 0 : cent;
  return (value / 100).toFixed(2);
};
