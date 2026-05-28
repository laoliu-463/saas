/**
 * @module api/sys
 * @description 系统管理 API 模块
 *
 * 提供系统管理后台所需的全部数据接口，按业务域划分为五大类：
 * 1. 用户管理 — CRUD、密码重置、角色分配、当前用户信息、数据权限、权限检查、主数据查询
 * 2. 角色管理 — CRUD、启用列表查询
 * 3. 部门管理 — 树形/列表查询、CRUD、统计、成员管理、分组管理
 * 4. 配置管理 — CRUD、分组查询
 * 5. 操作日志 — 分页查询
 *
 * 主数据接口（/users/master-data/**）为业务页面提供轻量级下拉选项数据，
 * 替代旧版 /users/assignable 兼容入口。
 */
import request from '../utils/request';

// ==================== 用户管理 ====================

/**
 * 分页查询用户列表
 *
 * @param params - 查询参数（分页、关键词、角色、状态等）
 * @returns 用户分页列表
 */
export const getUserPage = (params: any) => request.get('/users', { params });
/**
 * 查询可分配用户选项（旧兼容入口）
 *
 * @deprecated 业务页面优先使用 /users/master-data/** 系列接口
 * @param params - 筛选参数
 * @returns 可分配用户列表
 */
export const getAssignableUserOptions = (params: any) => request.get('/users/assignable', { params });
/** 获取用户详情 @param id - 用户 ID */
export const getUserById = (id: string) => request.get(`/users/${id}`);
/** 创建用户 @param data - 用户信息 */
export const createUser = (data: any) => request.post('/users', data);
/** 更新用户信息 @param id - 用户 ID @param data - 更新字段 */
export const updateUser = (id: string, data: any) => request.put(`/users/${id}`, data);
/** 删除用户 @param id - 用户 ID */
export const deleteUser = (id: string) => request.delete(`/users/${id}`);
/** 重置用户密码 @param id - 用户 ID @param data - 新密码数据 */
export const resetUserPassword = (id: string, data: any) => request.put(`/users/${id}/password`, data);
/** 分配用户角色 @param id - 用户 ID @param data - 角色 ID 列表 */
export const assignUserRoles = (id: string, data: any) => request.put(`/users/${id}/roles`, data);

/**
 * 获取当前登录用户信息
 * @returns 当前用户的完整信息（含角色、权限等）
 */
export const getCurrentUser = () => request.get('/users/current');

/**
 * 修改当前用户密码
 * @param data.oldPassword - 原密码
 * @param data.newPassword - 新密码
 */
export const changeCurrentUserPassword = (data: { oldPassword: string; newPassword: string }) =>
  request.put('/users/current/password', data);

/**
 * 获取当前用户的数据权限范围
 * @returns 数据范围配置（self / group / all）
 */
export const getCurrentUserDataScope = () => request.get('/users/current/data-scope');

/**
 * 检查当前用户是否拥有指定权限
 * @param data.resource - 资源标识
 * @param data.action - 操作类型
 * @returns 权限检查结果
 */
export const checkCurrentUserPermission = (data: { resource: string; action: string }) =>
  request.post('/users/current/permissions/check', data);
/**
 * 查询渠道人员主数据（下拉选项用）
 * @param params.keyword - 搜索关键词
 * @param params.limit - 返回数量上限
 * @returns 渠道人员列表
 */
export const getUserMasterChannels = (params?: { keyword?: string; limit?: number }) =>
  request.get('/users/master-data/channels', { params });

/**
 * 查询招商人员主数据（下拉选项用）
 * @param params.keyword - 搜索关键词
 * @param params.limit - 返回数量上限
 * @returns 招商人员列表
 */
export const getUserMasterRecruiters = (params?: { keyword?: string; limit?: number }) =>
  request.get('/users/master-data/recruiters', { params });

/**
 * 查询团队成员主数据（下拉选项用）
 * @param params.deptId - 部门 ID
 * @param params.keyword - 搜索关键词
 * @param params.limit - 返回数量上限
 * @returns 团队成员列表
 */
export const getUserMasterGroupMembers = (params?: { deptId?: string; keyword?: string; limit?: number }) =>
  request.get('/users/master-data/group-members', { params });

// ==================== 角色管理 ====================

/** 分页查询角色列表 @param params - 分页和筛选参数 */
export const getRolePage = (params: any) => request.get('/roles', { params });
/** 查询所有已启用角色 @returns 角色列表（用于下拉选择器） */
export const getRoleAll = () => request.get('/roles/enabled');
/** 获取角色详情 @param id - 角色 ID */
export const getRoleById = (id: string) => request.get(`/roles/${id}`);
/** 创建角色 @param data - 角色信息（名称、编码、权限等） */
export const createRole = (data: any) => request.post('/roles', data);
/** 更新角色 @param id - 角色 ID @param data - 更新字段 */
export const updateRole = (id: string, data: any) => request.put(`/roles/${id}`, data);
/** 删除角色 @param id - 角色 ID */
export const deleteRole = (id: string) => request.delete(`/roles/${id}`);

// ==================== 部门管理 ====================

/** 获取部门树形结构 @returns 嵌套树形部门列表 */
export const getDeptTree = () => request.get('/depts/tree');
/** 获取部门扁平列表 @returns 平铺部门列表 */
export const getDeptList = () => request.get('/depts');
/** 获取部门详情 @param id - 部门 ID */
export const getDeptById = (id: string) => request.get(`/depts/${id}`);
/** 创建部门 @param data - 部门信息 */
export const createDept = (data: any) => request.post('/depts', data);
/** 更新部门 @param id - 部门 ID @param data - 更新字段 */
export const updateDept = (id: string, data: any) => request.put(`/depts/${id}`, data);
/** 删除部门 @param id - 部门 ID */
export const deleteDept = (id: string) => request.delete(`/depts/${id}`);
/** 获取部门统计信息 @param id - 部门 ID @returns 人员数、业绩等统计 */
export const getDeptStats = (id: string) => request.get(`/depts/${id}/stats`);

/**
 * 获取部门成员列表
 * @param id - 部门 ID
 * @param params - 可选的筛选参数
 * @returns 成员列表
 */
export const getDeptMembers = (id: string, params?: Record<string, unknown>) =>
  request.get(`/depts/${id}/members`, { params });

/**
 * 获取部门下的分组列表
 * @param id - 部门 ID
 * @param params.deptType - 部门类型筛选
 * @returns 分组列表
 */
export const getDeptGroups = (id: string, params?: { deptType?: string }) =>
  request.get(`/depts/${id}/groups`, { params });

/**
 * 向分组添加成员
 * @param groupId - 分组 ID
 * @param data.userIds - 用户 ID 列表
 */
export const addDeptGroupMembers = (groupId: string, data: { userIds: string[] }) =>
  request.post(`/depts/groups/${groupId}/members`, data);

/**
 * 从分组移除成员
 * @param groupId - 分组 ID
 * @param data.userIds - 用户 ID 列表
 */
export const removeDeptGroupMembers = (groupId: string, data: { userIds: string[] }) =>
  request.delete(`/depts/groups/${groupId}/members`, { data });

// ==================== 配置管理 ====================

/** 分页查询配置项列表 @param params - 分页和筛选参数 */
export const getConfigPage = (params: any) => request.get('/configs', { params });
/** 获取分组配置（按 group 键聚合） @returns 分组后的配置对象 */
export const getConfigGrouped = () => request.get('/configs/grouped');
/** 获取配置详情 @param id - 配置 ID */
export const getConfigById = (id: string) => request.get(`/configs/${id}`);
/** 创建配置 @param data - 配置数据（key/value/group 等） */
export const createConfig = (data: any) => request.post('/configs', data);
/** 更新配置 @param id - 配置 ID @param data - 更新字段 */
export const updateConfig = (id: string, data: any) => request.put(`/configs/${id}`, data);
/** 删除配置 @param id - 配置 ID */
export const deleteConfig = (id: string) => request.delete(`/configs/${id}`);

// ==================== 操作日志 ====================

/**
 * 分页查询操作日志
 *
 * @param params - 查询参数（分页、操作人、时间范围、操作类型等）
 * @returns 操作日志分页列表
 */
export const getOperationLogPage = (params: any) => request.get('/operation-logs', { params });
