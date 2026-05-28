/**
 * @module api/auth
 * @description 认证（Authentication）API 客户端模块
 *
 * 提供用户登录、登出等认证相关接口。
 * 登录成功后，后端返回 accessToken 和 refreshToken，由 stores/auth.ts 持久化到 localStorage。
 * 登出时携带 refreshToken 通知后端使该 token 失效。
 *
 * 在前端架构中属于 API 层（api/），被 Login.vue 视图和 auth store 调用。
 */
import request from '../utils/request';

/**
 * 用户登录接口
 *
 * @param data - 登录请求体，包含 username 和 password 字段
 * @returns AxiosPromise，响应体包含 accessToken、refreshToken 和用户信息
 */
export const login = (data: any) => request.post('/auth/login', data);

/**
 * 用户登出接口
 *
 * 通知后端使当前 refresh token 失效，完成服务端会话清理。
 *
 * @param data - 登出请求体
 * @param data.accessToken - 可选，当前访问令牌
 * @param data.refreshToken - 必填，当前刷新令牌，用于后端定位并失效对应会话
 * @returns AxiosPromise
 */
export const logout = (data: { accessToken?: string; refreshToken: string }) => request.post('/auth/logout', data);
