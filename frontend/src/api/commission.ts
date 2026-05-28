/**
 * @module api/commission
 * @description 提成规则管理 API 客户端模块
 *
 * 提供提成规则（Commission Rule）的增删改查接口。
 * 提成规则定义了不同维度（达人、渠道、商品等）下的提成比例，用于业绩域计算最终佣金。
 *
 * 在前端架构中属于 API 层（api/），被 views/system/CommissionRuleList.vue 视图调用。
 */
import request from '../utils/request';

/**
 * 提成规则项接口
 *
 * 描述一条提成规则的完整数据结构。
 */
export interface CommissionRuleItem {
  /** 规则 ID（新建时可为空，由后端生成） */
  id?: string
  /** 维度类型，标识规则适用的维度（如 talent、channel、product 等） */
  dimensionType: string
  /** 维度 ID，指定具体关联的对象（如特定达人 ID），null 表示全局默认 */
  dimensionId?: string | null
  /** 提成类型，区分不同提成计算方式（如服务费提成、技术费提成等） */
  commissionType: string
  /** 提成比例，取值范围 0-1 之间的小数（如 0.3 表示 30%） */
  ratio: number
  /** 生效开始时间（ISO 8601 格式），null 表示不限制开始时间 */
  effectiveStart?: string | null
  /** 生效结束时间（ISO 8601 格式），null 表示长期有效 */
  effectiveEnd?: string | null
  /** 规则状态（0=禁用，1=启用） */
  status?: number
}

/**
 * 获取提成规则分页列表
 *
 * @param params - 可选的分页及筛选参数
 * @param params.page - 页码，默认 1
 * @param params.size - 每页条数，默认 20
 * @param params.dimensionType - 按维度类型筛选
 * @param params.commissionType - 按提成类型筛选
 * @returns AxiosPromise，响应体包含规则列表和分页元数据
 */
export const getCommissionRulePage = (params?: {
  page?: number
  size?: number
  dimensionType?: string
  commissionType?: string
}) => request.get('/commission-rules', { params });

/**
 * 创建提成规则
 *
 * @param data - 提成规则数据（不含 id）
 * @returns AxiosPromise，响应体包含新创建的规则（含后端生成的 id）
 */
export const createCommissionRule = (data: CommissionRuleItem) => request.post('/commission-rules', data);

/**
 * 更新提成规则
 *
 * @param id - 规则 ID
 * @param data - 更新后的规则数据
 * @returns AxiosPromise
 */
export const updateCommissionRule = (id: string, data: CommissionRuleItem) =>
  request.put(`/commission-rules/${id}`, data);

/**
 * 删除提成规则
 *
 * @param id - 规则 ID
 * @returns AxiosPromise
 */
export const deleteCommissionRule = (id: string) => request.delete(`/commission-rules/${id}`);
