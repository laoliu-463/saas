/**
 * @module api/dashboard
 * @description 数据看板（Dashboard）API 客户端模块
 *
 * 负责提供数据看板相关的后端接口调用，包括首页汇总数据等。
 * 所有请求通过统一的 request 实例（axios 封装）发出，自动携带鉴权 Header。
 *
 * 在前端架构中属于 API 层（api/），为 views/data/ 和 views/dashboard/ 下的
 * 视图组件提供数据获取能力。
 */
import request from '../utils/request';

/**
 * 获取数据看板汇总信息
 *
 * 用于首页或数据概览页面展示核心指标（订单数、金额、服务费等）。
 *
 * @param params - 可选的查询参数，后端按时间段、维度等条件筛选汇总数据
 * @returns AxiosPromise，响应体包含汇总数据对象
 */
export function getSummary(params?: any) {
  return request.get('/dashboard/summary', { params });
}
