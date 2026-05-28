/**
 * @module api/ruleCenter
 * @description 规则中心 API 客户端模块
 *
 * 规则中心是系统配置域的核心组件，通过 KV（键值对）方式管理业务规则配置。
 * 前端提供 schema 查询（定义可配置项）、值查询、批量更新、变更日志查询等功能。
 * 所有规则变更均记录操作日志，支持通过 eventId 追踪异步事件状态。
 *
 * 在前端架构中属于 API 层（api/），被 views/system/rule-center/ 下的视图调用。
 */
import request from '../utils/request';

/**
 * 获取规则中心 schema（配置项定义）
 *
 * 返回所有可配置规则项的定义，包括 key、名称、类型、默认值、校验规则等。
 * 前端根据 schema 动态渲染配置表单。
 *
 * @returns AxiosPromise，响应体包含配置项 schema 数组
 */
export const getRuleCenterSchema = () => request.get('/rule-center/schema');

/**
 * 获取规则中心当前所有配置值
 *
 * 返回当前生效的所有规则键值对。
 *
 * @returns AxiosPromise，响应体包含 Record<string, string> 格式的配置值
 */
export const getRuleCenterValues = () => request.get('/rule-center');

/**
 * 校验规则配置值（不落库）
 *
 * 前端提交配置值后，后端根据 schema 进行格式和业务规则校验，
 * 返回校验结果（通过 / 不通过及原因）。
 *
 * @param data - 待校验的配置值
 * @param data.values - 键值对，key 为规则项标识，value 为配置值
 * @returns AxiosPromise，响应体包含校验结果
 */
export const validateRuleCenter = (data: { values: Record<string, string> }) =>
  request.post('/rule-center/validate', data);

/**
 * 批量更新规则配置值
 *
 * 原子性地更新多条规则配置，所有变更记录到操作日志。
 *
 * @param data - 更新数据
 * @param data.values - 键值对，key 为规则项标识，value 为新配置值
 * @param data.changeReason - 可选，变更原因说明，记录到审计日志
 * @returns AxiosPromise
 */
export const batchUpdateRuleCenter = (data: { values: Record<string, string>; changeReason?: string }) =>
  request.put('/rule-center/batch', data);

/**
 * 获取规则变更日志分页列表
 *
 * 查询规则配置的历史变更记录，支持按 key 筛选。
 *
 * @param params - 查询参数
 * @param params.key - 可选，按规则 key 筛选变更记录
 * @param params.page - 页码
 * @param params.size - 每页条数
 * @returns AxiosPromise，响应体包含变更日志列表和分页元数据
 */
export const getRuleCenterChangeLogs = (params: { key?: string; page?: number; size?: number }) =>
  request.get('/rule-center/change-logs', { params });

/**
 * 查询规则中心异步事件状态
 *
 * 规则变更可能触发异步事件（如缓存刷新、下游通知等），
 * 通过 eventId 查询事件的执行状态。
 *
 * @param eventId - 事件 ID
 * @returns AxiosPromise，响应体包含事件状态信息
 */
export const getRuleCenterEventStatus = (eventId: string) =>
  request.get('/rule-center/events', { params: { eventId } });
