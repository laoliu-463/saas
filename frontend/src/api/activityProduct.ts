import request from '../utils/request';

export const getActivityProducts = (activityId: string | number, params: any) =>
  request.get(`/colonel/activities/${activityId}/products`, { params });

export const getActivityProductDetail = (activityId: string | number, productId: string | number) =>
  request.get(`/colonel/activities/${activityId}/products/${productId}`);

export const auditActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { approved: boolean; reason?: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/audit-result`, data);

export const assignActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { assigneeId: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/assignee`, data);

export const convertActivityProductLink = (
  activityId: string | number,
  productId: string | number,
  data?: { scene?: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK'; talentId?: string }
) => request.post(`/colonel/activities/${activityId}/products/${productId}/promotion-links`, data || { scene: 'PRODUCT_LIBRARY' });

export const followActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: {
    talentId?: string;
    talentName?: string;
    followStatus: string;
    content?: string;
    nextFollowTime?: string;
    operatorName?: string;
  }
) => request.post(`/colonel/activities/${activityId}/products/${productId}/follow`, data);

export const getActivityProductOperationLogs = (
  activityId: string | number,
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/colonel/activities/${activityId}/products/${productId}/operation-logs`, { params });

export const syncActivityProducts = (activityId: string | number) =>
  request.post(`/colonel/activities/${activityId}/products/sync`);
