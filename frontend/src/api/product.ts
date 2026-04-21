import request from '../utils/request';

// 商品列表 / 详情
export const getProductPage = (params: any) => request.get('/products/page', { params });
export const getProductById = (id: string) => request.get(`/products/${id}`);

// 活动
export const getActivityPage = (params: any) => request.get('/activities/page', { params });

// 商品操作
export const bindActivity = (id: string, data: { activityId: string }) =>
  request.put(`/products/${id}/bind-activity`, data);

export const assignProduct = (id: string, data: { assigneeId: string }) =>
  request.put(`/products/${id}/assign`, data);

export const auditProduct = (id: string, data: { approved: boolean; reason?: string }) =>
  request.put(`/products/${id}/audit`, data);

export const generatePromotionLink = (id: string, data?: { externalUniqueId?: string; promotionScene?: number; needShortLink?: boolean }) =>
  request.post(`/products/${id}/promotion-link`, data || {});
