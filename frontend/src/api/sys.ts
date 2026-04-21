import request from '../utils/request';

// 用户
export const getUserPage = (params: any) => request.get('/sys/users/page', { params });
export const getUserById = (id: string) => request.get(`/sys/users/${id}`);
export const createUser = (data: any) => request.post('/sys/users', data);
export const updateUser = (id: string, data: any) => request.put(`/sys/users/${id}`, data);
export const deleteUser = (id: string) => request.delete(`/sys/users/${id}`);
export const resetUserPassword = (id: string, data: any) => request.put(`/sys/users/${id}/reset-password`, data);
export const assignUserRoles = (id: string, data: any) => request.put(`/sys/users/${id}/roles`, data);

// 角色
export const getRolePage = (params: any) => request.get('/sys/roles/page', { params });
export const getRoleAll = () => request.get('/sys/roles/all');
export const getRoleById = (id: string) => request.get(`/sys/roles/${id}`);
export const createRole = (data: any) => request.post('/sys/roles', data);
export const updateRole = (id: string, data: any) => request.put(`/sys/roles/${id}`, data);
export const deleteRole = (id: string) => request.delete(`/sys/roles/${id}`);
