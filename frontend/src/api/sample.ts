import request from '../utils/request';

// 寄样单 CRUD
export const getSamplePage = (params: any) => request.get('/samples', { params });
export const getSampleFilterOptions = () => request.get('/samples/filter-options');
export const getSampleById = (id: string) => request.get(`/samples/${id}`);
export const createSample = (data: any) => request.post('/samples', data);
export const checkSampleEligibility = (data: any) => request.post('/samples/eligibility-check', data);
export const actionSample = (id: string, data: any) => request.put(`/samples/${id}/status`, data);
export const deleteSample = (id: string) => request.delete(`/samples/${id}`);
export const exportSamples = (params: any) => request.get('/samples/exports', { params, responseType: 'blob' });
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

// 寄样状态流转矩阵
export const getSampleStatusTransitions = () => request.get('/samples/status-transitions');

// 寄样状态日志
export const getSampleStatusLogs = (id: string) => request.get(`/samples/${id}/status-logs`);

// 批量发货（运营）
export const batchApproveSamples = (data: { requestNos: string[]; remark?: string }) =>
  request.post('/samples/batch-approve', data);

export const batchRejectSamples = (data: { requestNos: string[]; remark: string }) =>
  request.post('/samples/batch-reject', data);

export const batchShipSamples = (data: { items: { requestNo: string; trackingNo: string; shipperCode?: string }[] }) =>
  request.post('/samples/batch-ship', data);

export const syncSampleLogistics = (id: string) => request.post(`/samples/${id}/logistics/sync`);

export const getSampleLogistics = (id: string) => request.get(`/samples/${id}/logistics`);

export const syncAllSampleLogistics = () => request.post('/admin/samples/logistics/sync');

export const downloadLogisticsImportTemplate = () =>
  request.get('/samples/logistics/import-template', { responseType: 'blob' });

export const importSampleLogistics = (file: File, allowOverwrite = false) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/samples/logistics/import?allowOverwrite=${allowOverwrite}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};
