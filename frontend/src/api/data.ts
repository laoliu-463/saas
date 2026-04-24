import request from '../utils/request';

export const getOrderPage = (params: any) => request.get('/orders', { params });
export const getMetrics = (params?: any) => request.get('/dashboard/metrics', { params });

// 订单解密
export const decryptOrders = (orderIds: string[]) =>
  request.post('/orders/phone-decryptions', { orderIds });

export const exportOrders = (params: any) => request.get('/orders/exports', { params, responseType: 'blob' });
export const getExclusiveTalentStatus = () => request.get('/operations/exclusive-talents');
export const getExclusiveMerchantStatus = () => request.get('/operations/exclusive-merchants');
