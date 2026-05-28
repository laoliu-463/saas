/**
 * @module api/activity
 * @description 活动管理 API 客户端模块
 *
 * 提供抖音精选联盟活动的查询和同步接口。
 * 活动是抖音团长业务的核心载体，一个活动可包含多个商品，达人通过活动下的推广链接获取佣金。
 *
 * 接口分为两类路径：
 * - `/activities/**`：通用活动查询，面向所有角色
 * - `/colonel/activities/**`：团长专属活动管理（含同步操作）
 *
 * 在前端架构中属于 API 层（api/），被 views/product/ 下的活动列表等视图调用。
 */
import request from '../utils/request';

/**
 * 获取活动分页列表（通用）
 *
 * @param params - 分页及筛选参数（page、size、keyword、status 等）
 * @returns AxiosPromise，响应体包含活动列表和分页元数据
 */
export const getActivityPage = (params: any) => request.get('/activities', { params });

/**
 * 获取团长活动分页列表
 *
 * 仅返回当前团长用户关联的活动数据，支持团长维度的筛选。
 *
 * @param params - 分页及筛选参数
 * @returns AxiosPromise，响应体包含团长活动列表
 */
export const getColonelActivityPage = (params: any) => request.get('/colonel/activities', { params });

/**
 * 获取活动在抖音侧的详细信息
 *
 * 调用后端同步抖音开放平台接口，返回活动在精选联盟中的实时状态。
 *
 * @param activityId - 活动 ID
 * @returns AxiosPromise，响应体包含抖音侧活动详情（状态、商品数、达人数等）
 */
export const getActivityDouyinDetail = (activityId: number) => request.get(`/activities/${activityId}/douyin-detail`);

/**
 * 手动触发团长活动同步
 *
 * 将本地活动数据与抖音开放平台进行一次增量同步。
 * 同步完成后活动下商品、达人关系等数据会更新。
 *
 * @param activityId - 活动 ID（支持字符串或数字类型）
 * @returns AxiosPromise
 */
export const syncColonelActivity = (activityId: string | number) => request.post(`/colonel/activities/${activityId}/sync`);
