import request from '../utils/request';

// 共享商品库 / 选品候选（legacy /products 读路径；写操作请用 activityProduct.ts）
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
