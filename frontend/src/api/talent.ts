import request from '../utils/request';

// CRM talents
export const getTalentPage = (params: any) => request.get('/talents/page', { params });
export const getTalentList = (params: any) => getTalentPage(params);
export const getTalentPublic = (params: any) => request.get('/talents/public', { params });
export const getTalentPrivate = (params: any) => request.get('/talents/private', { params });
export const getTalentById = (id: string) => request.get(`/talents/${id}`);
export const createTalent = (data: any) => request.post('/talents', data);
export const updateTalent = (id: string, data: any) => request.put(`/talents/${id}`, data);
export const deleteTalent = (id: string) => request.delete(`/talents/${id}`);
export const claimTalent = (id: string) => request.post(`/talents/${id}/claim`);
export const exclusiveCheck = (id: string) => request.get(`/talents/${id}/exclusive-check`);
