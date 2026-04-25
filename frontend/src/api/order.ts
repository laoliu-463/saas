import request from '../utils/request';

export function syncOrders(startTime: string, endTime: string) {
  return request.post('/orders/sync', { startTime, endTime });
}

export function getOrders(params: any) {
  return request.get('/orders', { params });
}

export function getUnattributedOrders(params: any) {
  return request.get('/orders/unattributed', { params });
}

/** @deprecated use syncOrders instead */
export function triggerOrderSync() {
  const now = new Date();
  const start = new Date();
  start.setDate(start.getDate() - 30);
  
  const formatDate = (d: Date) => d.toISOString().replace('T', ' ').split('.')[0];
  
  return syncOrders(formatDate(start), formatDate(now));
}

