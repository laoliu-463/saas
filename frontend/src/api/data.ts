import request from '../utils/request';

export const getOrderPage = (params: any) => request.get('/data/orders', { params });
export const getMetrics = (params?: any, config: any = {}) => request.get('/dashboard/metrics', { params, ...config });

export const exportOrders = (params: any) => request.get('/orders/exports', { params, responseType: 'blob' });
export const exportActivities = (params: any) => request.get('/activities/exports', { params, responseType: 'blob' });
export const getExclusiveTalentStatus = () => request.get('/operations/exclusive-talents');
export const getExclusiveMerchantStatus = () => request.get('/operations/exclusive-merchants');
