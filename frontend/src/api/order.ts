import request from '../utils/request';

export function triggerOrderSync() {
  return request.post('/order-sync-jobs');
}
