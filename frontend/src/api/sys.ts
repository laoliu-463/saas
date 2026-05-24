import request from '../utils/request';

// 用户
export const getUserPage = (params: any) => request.get('/users', { params });
// 旧商品分配候选兼容入口；业务页面优先使用 /users/master-data/**。
export const getAssignableUserOptions = (params: any) => request.get('/users/assignable', { params });
export const getUserById = (id: string) => request.get(`/users/${id}`);
export const createUser = (data: any) => request.post('/users', data);
export const updateUser = (id: string, data: any) => request.put(`/users/${id}`, data);
export const deleteUser = (id: string) => request.delete(`/users/${id}`);
export const resetUserPassword = (id: string, data: any) => request.put(`/users/${id}/password`, data);
export const assignUserRoles = (id: string, data: any) => request.put(`/users/${id}/roles`, data);
export const getCurrentUser = () => request.get('/users/current');
export const changeCurrentUserPassword = (data: { oldPassword: string; newPassword: string }) =>
  request.put('/users/current/password', data);
export const getCurrentUserDataScope = () => request.get('/users/current/data-scope');
export const checkCurrentUserPermission = (data: { resource: string; action: string }) =>
  request.post('/users/current/permissions/check', data);
export const getUserMasterChannels = (params?: { keyword?: string; limit?: number }) =>
  request.get('/users/master-data/channels', { params });
export const getUserMasterRecruiters = (params?: { keyword?: string; limit?: number }) =>
  request.get('/users/master-data/recruiters', { params });
export const getUserMasterGroupMembers = (params?: { deptId?: string; keyword?: string; limit?: number }) =>
  request.get('/users/master-data/group-members', { params });

// 角色
export const getRolePage = (params: any) => request.get('/roles', { params });
export const getRoleAll = () => request.get('/roles/enabled');
export const getRoleById = (id: string) => request.get(`/roles/${id}`);
export const createRole = (data: any) => request.post('/roles', data);
export const updateRole = (id: string, data: any) => request.put(`/roles/${id}`, data);
export const deleteRole = (id: string) => request.delete(`/roles/${id}`);

// 部门
export const getDeptTree = () => request.get('/depts/tree');
export const getDeptList = () => request.get('/depts');
export const getDeptById = (id: string) => request.get(`/depts/${id}`);
export const createDept = (data: any) => request.post('/depts', data);
export const updateDept = (id: string, data: any) => request.put(`/depts/${id}`, data);
export const deleteDept = (id: string) => request.delete(`/depts/${id}`);
export const getDeptStats = (id: string) => request.get(`/depts/${id}/stats`);
export const getDeptMembers = (id: string, params?: Record<string, unknown>) =>
  request.get(`/depts/${id}/members`, { params });
export const getDeptGroups = (id: string, params?: { deptType?: string }) =>
  request.get(`/depts/${id}/groups`, { params });
export const addDeptGroupMembers = (groupId: string, data: { userIds: string[] }) =>
  request.post(`/depts/groups/${groupId}/members`, data);
export const removeDeptGroupMembers = (groupId: string, data: { userIds: string[] }) =>
  request.delete(`/depts/groups/${groupId}/members`, { data });

// 配置
export const getConfigPage = (params: any) => request.get('/configs', { params });
export const getConfigGrouped = () => request.get('/configs/grouped');
export const getConfigById = (id: string) => request.get(`/configs/${id}`);
export const createConfig = (data: any) => request.post('/configs', data);
export const updateConfig = (id: string, data: any) => request.put(`/configs/${id}`, data);
export const deleteConfig = (id: string) => request.delete(`/configs/${id}`);

// 操作日志
export const getOperationLogPage = (params: any) => request.get('/operation-logs', { params });
