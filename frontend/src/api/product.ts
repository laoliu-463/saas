import request from '../utils/request';

// 商品列表 / 详情
export const getProducts = (params: any) => request.get('/products', { params });
export const getProductFilterOptions = () => request.get('/products/filter-options');
export const getProductLibraryCategories = () => request.get('/products/categories');
export const getProductPickPage = (params: any) => request.get('/products/picks', { params });
export const applyQuickSample = (relationId: string, data: any) =>
  request.post(`/products/${relationId}/quick-sample`, data);
export const listPartners = (params: { keyword?: string; type?: string; page?: number; size?: number }) =>
  request.get('/colonel/partners', { params });
export const getPartnerDetail = (id: string | number, params?: { type?: string }) =>
  request.get(`/colonel/partners/${id}`, { params });
export const getPartnerProducts = (
  id: string | number,
  params?: { type?: string; page?: number; size?: number }
) => request.get(`/colonel/partners/${id}/products`, { params });

// 业务主链路操作
export const auditProduct = (id: string | number, data: { approved: boolean; reason?: string }) =>
  request.put(`/products/${id}/audit-result`, data);

export const bindProductActivity = (id: string | number, data: { activityId: string | number }) =>
  request.put(`/products/${id}/activity`, data);

export const assignProduct = (id: string | number, data: { assigneeId: string }) =>
  request.put(`/products/${id}/assignee`, data);

export const convertProductLink = (id: string | number, data?: { externalUniqueId?: string; promotionScene?: number; needShortLink?: boolean }) =>
  request.post(`/products/${id}/promotion-links`, data || {});

export const followProduct = (
  id: string | number,
  data: {
    talentId?: string;
    talentName?: string;
    followStatus: string;
    content?: string;
    nextFollowTime?: string;
    operatorName?: string;
  }
) => request.post(`/products/${id}/follow`, data);
