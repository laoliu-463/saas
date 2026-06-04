/**
 * @module api/data
 * @description 数据看板与运营数据 API 客户端模块
 *
 * 提供数据看板页面的订单查询、指标统计、导出以及独家状态等运营数据接口。
 * 与 api/dashboard.ts 的区别在于：本模块专注于明细数据查询和导出，
 * dashboard.ts 侧重汇总概览。
 *
 * 在前端架构中属于 API 层（api/），被 views/data/ 下的数据看板和订单列表视图调用。
 */
import request from '../utils/request';

/**
 * 获取数据看板订单分页列表
 *
 * @param params - 分页及筛选参数（page、size、时间范围、状态等）
 * @returns AxiosPromise，响应体包含订单列表和分页元数据
 */
export const getOrderPage = (params: any) => request.get('/data/orders', { params });

/**
 * 获取订单明细分页列表
 *
 * 返回逐订单粒度的明细数据，含双轨金额（预估/结算）。
 *
 * @param params - 分页及筛选参数
 * @returns AxiosPromise，响应体包含 OrderDetailVO 列表和分页元数据
 */
export const getOrderDetailPage = (params: any) => request.get('/data/orders/detail', { params });

/**
 * 获取订单汇总统计
 *
 * 返回指定筛选条件下的订单数量、金额、服务费等汇总指标。
 *
 * @param params - 筛选参数，与订单列表一致
 * @returns AxiosPromise，响应体包含汇总统计数据
 */
export const getOrderSummary = (params: any) => request.get('/data/orders/summary', { params });

/**
 * 获取仪表盘核心指标
 *
 * 返回首页展示的全局关键指标（总订单数、总金额、服务费收入、毛利等）。
 *
 * @param params - 可选的筛选参数（时间范围等）
 * @param config - 可选的 axios 请求配置（如 timeout 覆盖）
 * @returns AxiosPromise，响应体包含指标数据对象
 */
export const getMetrics = (params?: any, config: any = {}) => request.get('/dashboard/metrics', { params, ...config });

/**
 * 导出订单数据
 *
 * 以文件流（blob）形式下载订单数据，浏览器端触发文件下载。
 *
 * @param params - 导出筛选参数（时间范围、状态等）
 * @returns AxiosPromise，响应类型为 blob（Excel 文件）
 */
export const exportOrders = (params: any) => request.get('/orders/exports', { params, responseType: 'blob' });

/**
 * 导出订单明细 CSV
 *
 * 以文件流（blob）形式下载订单明细数据，含双轨金额。
 *
 * @param params - 导出筛选参数
 * @returns AxiosPromise，响应类型为 blob（CSV 文件）
 */
export const exportOrderDetail = (params: any) => request.get('/orders/exports/detail', { params, responseType: 'blob' });

/**
 * 导出活动数据
 *
 * 以文件流（blob）形式下载活动数据。
 *
 * @param params - 导出筛选参数
 * @returns AxiosPromise，响应类型为 blob（Excel 文件）
 */
export const exportActivities = (params: any) => request.get('/activities/exports', { params, responseType: 'blob' });

/**
 * 获取独家达人状态列表
 *
 * 返回当前系统中所有独家达人的状态概览。
 *
 * @returns AxiosPromise，响应体包含独家达人状态列表
 */
export const getExclusiveTalentStatus = () => request.get('/operations/exclusive-talents');

/**
 * 获取独家商家状态列表
 *
 * 返回当前系统中所有独家商家的状态概览。
 *
 * @returns AxiosPromise，响应体包含独家商家状态列表
 */
export const getExclusiveMerchantStatus = () => request.get('/operations/exclusive-merchants');
