import request from '../utils/request';

export const getRuleCenterSchema = () => request.get('/rule-center/schema');
export const getRuleCenterValues = () => request.get('/rule-center');
export const validateRuleCenter = (data: { values: Record<string, string> }) =>
  request.post('/rule-center/validate', data);
export const updateRuleCenterGroup = (groupCode: string, data: { values: Record<string, string>; changeReason?: string }) =>
  request.put(`/rule-center/groups/${groupCode}`, data);
export const batchUpdateRuleCenter = (data: { values: Record<string, string>; changeReason?: string }) =>
  request.put('/rule-center/batch', data);
export const getRuleCenterChangeLogs = (params: { key?: string; page?: number; size?: number }) =>
  request.get('/rule-center/change-logs', { params });
export const getRuleCenterEventStatus = (eventId: string) =>
  request.get('/rule-center/events', { params: { eventId } });
