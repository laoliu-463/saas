import request from '../utils/request';

// 用户
export const getUserPage = (params: any) => request.get('/users', { params });
export const getAssignableUserOptions = (params: any) => request.get('/users/assignable', { params });
export const getUserById = (id: string) => request.get(`/users/${id}`);
export const createUser = (data: any) => request.post('/users', data);
export const updateUser = (id: string, data: any) => request.put(`/users/${id}`, data);
export const deleteUser = (id: string) => request.delete(`/users/${id}`);
export const resetUserPassword = (id: string, data: any) => request.put(`/users/${id}/password`, data);
export const assignUserRoles = (id: string, data: any) => request.put(`/users/${id}/roles`, data);

// 角色
export const getRolePage = (params: any) => request.get('/roles', { params });
export const getRoleAll = () => request.get('/roles/enabled');
export const getRoleById = (id: string) => request.get(`/roles/${id}`);
export const createRole = (data: any) => request.post('/roles', data);
export const updateRole = (id: string, data: any) => request.put(`/roles/${id}`, data);
export const deleteRole = (id: string) => request.delete(`/roles/${id}`);

// 配置
export const getConfigPage = (params: any) => request.get('/configs', { params });
export const getConfigGrouped = () => request.get('/configs/grouped');
export const getConfigById = (id: string) => request.get(`/configs/${id}`);
export const createConfig = (data: any) => request.post('/configs', data);
export const updateConfig = (id: string, data: any) => request.put(`/configs/${id}`, data);
export const deleteConfig = (id: string) => request.delete(`/configs/${id}`);

// 操作日志
export const getOperationLogPage = (params: any) => request.get('/operation-logs', { params });
