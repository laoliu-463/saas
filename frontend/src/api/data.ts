import request from '../utils/request';

export const getOrderPage = (params: any) => request.get('/data/orders/page', { params });
export const getMetrics = (params?: any) => request.get('/data/metrics', { params });

// 订单解密
export const decryptOrders = (orderIds: string[]) =>
  request.post('/data/orders/decrypt', { orderIds });
