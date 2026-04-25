import request from '../utils/request';

export function getSummary() {
  return request.get('/dashboard/summary');
}
