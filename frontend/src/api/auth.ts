import request from '../utils/request';

export const login = (data: any) => request.post('/auth/login', data);
