import request from '../utils/request';

// 寄样单 CRUD
export const getSamplePage = (params: any) => request.get('/samples', { params });
export const getSampleById = (id: string) => request.get(`/samples/${id}`);
export const createSample = (data: any) => request.post('/samples', data);
export const actionSample = (id: string, data: any) => request.put(`/samples/${id}/status`, data);
export const deleteSample = (id: string) => request.delete(`/samples/${id}`);
export const searchSampleProducts = (params: any) => request.get('/samples/product-candidates', { params });

// 寄样台 — 达人搜索（数据源：crawler_talent_info）
export const searchSampleTalents = (params: {
  keyword?: string;
  region?: string;
  minFans?: number;
  maxFans?: number;
  minScore?: number;
  page?: number;
  size?: number;
}) => request.get('/samples/talent-candidates', { params });

// 寄样看板
export const getSampleBoard = () => request.get('/samples/board');
