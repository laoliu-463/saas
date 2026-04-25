import request from '../utils/request';

export const getProductOperationLogs = (
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/products/${productId}/operation-logs`, { params });

export const getActivityProductOperationLogs = (
  activityId: string | number,
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/colonel/activities/${activityId}/products/${productId}/operation-logs`, { params });
