import request from '../utils/request';

export function getSummary(params?: any) {
  return request.get('/dashboard/summary', { params });
}
