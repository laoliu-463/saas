/**
 * @module api/sample
 * @description 寄样管理 API 模块
 *
 * 负责寄样（样品寄送）全生命周期管理，涵盖：
 * - 寄样单 CRUD（创建、查询、状态变更、删除）
 * - 寄样看板（可视化状态统计）
 * - 达人搜索（寄样台选达人）
 * - 商品搜索（寄样台选商品）
 * - 资格校验（eligibility check）
 * - 批量操作（批量审批、批量驳回、批量发货）
 * - 物流管理（物流同步、物流查询、导入模板、批量导入）
 * - 状态流转矩阵查询
 * - 状态变更日志
 * - 数据导出
 *
 * 寄样业务流程：
 * 申请 -> 审批 -> 发货 -> 签收 -> 作业完成（由订单已同步事件驱动）
 *
 * 状态流转矩阵定义了各状态间的合法转换路径和操作角色。
 */
import request from '../utils/request';

/**
 * 分页查询寄样单列表
 *
 * @param params - 查询参数（分页、状态筛选、达人、商品等）
 * @returns 寄样单分页列表
 */
export const getSamplePage = (params: any) => request.get('/samples', { params });
/**
 * 获取寄样筛选器选项
 * @returns 动态筛选选项数据
 */
export const getSampleFilterOptions = () => request.get('/samples/filter-options');

/**
 * 获取寄样单详情
 * @param id - 寄样单 ID
 * @returns 寄样单完整信息
 */
export const getSampleById = (id: string) => request.get(`/samples/${id}`);

/**
 * 创建寄样单
 * @param data - 寄样申请数据（达人、商品、数量等）
 * @returns 创建结果
 */
export const createSample = (data: any) => request.post('/samples', data);

/**
 * 校验寄样申请资格
 *
 * 在创建寄样单前调用，检查达人是否满足寄样条件（粉丝量、等级等门槛）。
 *
 * @param data - 校验参数
 * @returns 资格校验结果（是否通过、不通过原因）
 */
export const checkSampleEligibility = (data: any) => request.post('/samples/eligibility-check', data);

/**
 * 执行寄样单状态变更操作
 *
 * 根据当前状态和操作类型，驱动寄样单流转到下一状态。
 * 可用操作取决于状态流转矩阵（通过 getSampleStatusTransitions 查询）。
 *
 * @param id - 寄样单 ID
 * @param data - 状态变更操作数据（action 类型、备注等）
 * @returns 状态变更结果
 */
export const actionSample = (id: string, data: any) => request.put(`/samples/${id}/status`, data);

/**
 * 删除寄样单
 * @param id - 寄样单 ID
 * @returns 删除结果
 */
export const deleteSample = (id: string) => request.delete(`/samples/${id}`);

/**
 * 导出寄样数据
 * @param params - 筛选条件
 * @returns Blob 数据，前端创建 Excel 下载链接
 */
export const exportSamples = (params: any) => request.get('/samples/exports', { params, responseType: 'blob' });

/**
 * 搜索可寄样商品候选列表
 *
 * 用于寄样台的商品选择器，返回满足寄样条件的商品列表。
 *
 * @param params - 搜索参数（关键词等）
 * @returns 商品候选列表
 */
export const searchSampleProducts = (params: any) => request.get('/samples/product-candidates', { params });

/**
 * 搜索可寄样达人候选列表
 *
 * 数据源为爬虫采集的达人信息表（crawler_talent_info），
 * 用于寄样台的达人选择器。
 *
 * @param params.keyword - 搜索关键词（达人昵称或抖音号）
 * @param params.region - 地区筛选
 * @param params.minFans - 最低粉丝量
 * @param params.maxFans - 最高粉丝量
 * @param params.minScore - 最低评分
 * @param params.page - 页码
 * @param params.size - 每页数量
 * @returns 达人候选分页列表
 */
export const searchSampleTalents = (params: {
  keyword?: string;
  region?: string;
  minFans?: number;
  maxFans?: number;
  minScore?: number;
  page?: number;
  size?: number;
}) => request.get('/samples/talent-candidates', { params });

/**
 * 获取寄样看板数据
 *
 * 返回各状态维度的寄样单统计，用于看板页面的可视化展示。
 *
 * @returns 看板统计数据
 */
export const getSampleBoard = () => request.get('/samples/board');

/**
 * 获取寄样状态流转矩阵
 *
 * 返回合法的状态转换路径和对应操作，用于前端动态渲染操作按钮。
 *
 * @returns 状态流转配置（states 数组 + transitions 数组）
 */
export const getSampleStatusTransitions = () => request.get('/samples/status-transitions');

/**
 * 查询寄样单状态变更日志
 *
 * @param id - 寄样单 ID
 * @returns 状态变更历史记录列表
 */
export const getSampleStatusLogs = (id: string) => request.get(`/samples/${id}/status-logs`);

/**
 * 批量审批通过寄样申请
 *
 * @param data.requestNos - 寄样单编号列表
 * @param data.remark - 审批备注（可选）
 * @returns 批量审批结果
 */
export const batchApproveSamples = (data: { requestNos: string[]; remark?: string }) =>
  request.post('/samples/batch-approve', data);

/**
 * 批量驳回寄样申请
 *
 * @param data.requestNos - 寄样单编号列表
 * @param data.remark - 驳回原因（必填）
 * @returns 批量驳回结果
 */
export const batchRejectSamples = (data: { requestNos: string[]; remark: string }) =>
  request.post('/samples/batch-reject', data);

/**
 * 批量发货寄样
 *
 * 为多个寄样单填写物流信息并标记为已发货。
 *
 * @param data.items - 发货项列表
 * @param data.items[].requestNo - 寄样单编号
 * @param data.items[].trackingNo - 快递单号
 * @param data.items[].shipperCode - 快递公司编码（可选）
 * @returns 批量发货结果
 */
export const batchShipSamples = (data: { items: { requestNo: string; trackingNo: string; shipperCode?: string }[] }) =>
  request.post('/samples/batch-ship', data);

/**
 * 同步单个寄样单的物流信息
 *
 * 触发后端从快递 100 等物流平台查询最新物流状态并更新。
 *
 * @param id - 寄样单 ID
 * @returns 物流同步结果
 */
export const syncSampleLogistics = (id: string) => request.post(`/samples/${id}/logistics/sync`);

/**
 * 查询寄样单物流详情
 *
 * @param id - 寄样单 ID
 * @returns 物流轨迹信息
 */
export const getSampleLogistics = (id: string) => request.get(`/samples/${id}/logistics`);

/**
 * 全量同步所有寄样单物流
 *
 * 管理员操作，批量触发所有待跟踪寄样单的物流状态更新。
 *
 * @returns 全量同步结果
 */
export const syncAllSampleLogistics = () => request.post('/admin/samples/logistics/sync');

/**
 * 下载物流导入模板
 *
 * @returns Excel 模板文件 Blob
 */
export const downloadLogisticsImportTemplate = () =>
  request.get('/samples/logistics/import-template', { responseType: 'blob' });

/**
 * 批量导入物流信息
 *
 * 通过 Excel 文件批量更新寄样单的快递单号和物流公司。
 *
 * @param file - Excel 文件
 * @param allowOverwrite - 是否允许覆盖已有物流信息，默认 false
 * @returns 导入结果（成功/失败行数）
 */
export const importSampleLogistics = (file: File, allowOverwrite = false) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/samples/logistics/import?allowOverwrite=${allowOverwrite}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};
