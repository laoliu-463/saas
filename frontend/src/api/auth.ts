import api from './index';

export const login = (data: any) => api.post('/auth/login', data);
