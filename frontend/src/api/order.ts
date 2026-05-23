import request from '../utils/request';

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

export function syncOrders(startTime: string, endTime: string, config: any = {}) {
  return request.post('/orders/sync', { startTime, endTime }, config);
}

export function getOrders(params: any, config: any = {}) {
  return request.get('/orders', { params, ...config });
}

export function getUnattributedOrders(params: any) {
  return request.get('/orders/unattributed', { params });
}

export function getOrderStats(params?: any) {
  return request.get('/orders/stats', { params });
}

export function getOrderFilterOptions(params?: any) {
  return request.get('/orders/filter-options', { params });
}

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
