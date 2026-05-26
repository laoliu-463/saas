import request from '../utils/request';

export const login = (data: any) => request.post('/auth/login', data);
export const logout = (data: { accessToken?: string; refreshToken: string }) => request.post('/auth/logout', data);
