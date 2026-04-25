import request from '../utils/request';

export const getProductPromotionLinks = (
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/products/${productId}/promotion-links/history`, { params });
