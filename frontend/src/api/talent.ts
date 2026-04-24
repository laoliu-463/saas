import request from '../utils/request';

// CRM talents
export const getTalentPage = (params: any) => request.get('/talents', { params });
export const getTalentList = (params: any) => getTalentPage(params);
export const getTalentPublic = (params: any) => request.get('/talents/pools/public', { params });
export const getTalentPrivate = (params: any) => request.get('/talents/pools/private', { params });
export const getTalentById = (id: string) => request.get(`/talents/${id}`);
export const createTalent = (data: any) => request.post('/talents', data);
export const updateTalent = (id: string, data: any) => request.put(`/talents/${id}`, data);
export const deleteTalent = (id: string) => request.delete(`/talents/${id}`);
export const claimTalent = (id: string) => request.post(`/talents/${id}/claims`);
export const releaseTalent = (id: string) => request.post(`/talents/${id}/release`);
export const refreshTalent = (id: string) => request.post(`/talents/${id}/refresh`);
export const refreshWeeklyTalents = () => request.post('/talents/refresh/weekly');
export const manualFillTalent = (id: string, data: any) => request.put(`/talents/${id}/manual-fill`, data);
export const getLatestEnrichTask = (id: string) => request.get(`/talents/${id}/enrich-task/latest`);
export const exclusiveCheck = (id: string) => request.get(`/talents/${id}/exclusive-status`);
