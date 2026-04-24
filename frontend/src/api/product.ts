import request from '../utils/request';

// 商品列表 / 详情（兼容旧接口）
export const getProductPage = (params: any) => request.get('/products', { params });
export const getProductById = (id: string) => request.get(`/products/${id}`);

// 活动列表 / 活动商品列表
export const getActivityPage = (params: any) => request.get('/activities', { params });
export const getColonelActivityPage = (params: any) => request.get('/colonel/activities', { params });
export const getColonelActivityProducts = (activityId: string | number, params: any) =>
  request.get(`/colonel/activities/${activityId}/products`, { params });
export const getActivityDouyinDetail = (activityId: number) =>
  request.get(`/activities/${activityId}/douyin-detail`);

// 活动商品主链路
export const getColonelActivityProductDetail = (activityId: string | number, productId: string | number) =>
  request.get(`/colonel/activities/${activityId}/products/${productId}`);

export const bindColonelActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { boundActivityId: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/bind-activity`, data);

export const assignColonelActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { assigneeId: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/assignee`, data);

export const auditColonelActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { approved: boolean; reason?: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/audit-result`, data);

export const generateColonelActivityPromotionLink = (
  activityId: string | number,
  productId: string | number,
  data?: { externalUniqueId?: string; promotionScene?: number; needShortLink?: boolean }
) => request.post(`/colonel/activities/${activityId}/products/${productId}/promotion-links`, data || {});

export const getColonelActivityProductLogs = (
  activityId: string | number,
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/colonel/activities/${activityId}/products/${productId}/operation-logs`, { params });

// 商品操作（兼容旧接口）
export const bindActivity = (id: string, data: { activityId: string }) =>
  request.put(`/products/${id}/activity`, data);

export const assignProduct = (id: string, data: { assigneeId: string }) =>
  request.put(`/products/${id}/assignee`, data);

export const auditProduct = (id: string, data: { approved: boolean; reason?: string }) =>
  request.put(`/products/${id}/audit-result`, data);

export const generatePromotionLink = (id: string, data?: { externalUniqueId?: string; promotionScene?: number; needShortLink?: boolean }) =>
  request.post(`/products/${id}/promotion-links`, data || {});

