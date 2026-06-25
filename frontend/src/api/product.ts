/**
 * @module api/product
 * @description 商品库（共享商品库 / 选品候选）API 模块
 *
 * 提供共享商品库的只读查询接口，用于商品库页面和选品场景。
 * 写操作（审核、转链、置顶等）请使用 activityProduct.ts 模块。
 *
 * 另外包含合作伙伴（partner）相关查询接口，用于商品合作管理：
 * - 合作伙伴列表 / 详情 / 商品查询
 */
import type { AxiosRequestConfig } from 'axios'
import request from '../utils/request';

type RequestConfig = AxiosRequestConfig & { suppressErrorNotice?: boolean }

const PRODUCT_LIBRARY_QUERY_TIMEOUT_MS = 30000

/**
 * 分页查询共享商品库列表
 *
 * @param params - 查询参数（分页、分类、关键词、筛选条件）
 * @returns 商品分页列表
 */
export const getProducts = (params: any, config: RequestConfig = {}) =>
  request.get('/products', {
    timeout: PRODUCT_LIBRARY_QUERY_TIMEOUT_MS,
    params,
    ...config
  });
/**
 * 获取商品筛选器选项
 * @returns 动态筛选选项（品牌、类目等）
 */
export const getProductFilterOptions = () => request.get('/products/filter-options');

/**
 * 获取商品库分类树
 * @returns 分类层级数据
 */
export const getProductLibraryCategories = () => request.get('/products/categories');

/**
 * 分页查询选品候选列表
 *
 * 选品候选是经过筛选的商品子集，用于达人推荐或活动参与。
 *
 * @param params - 查询参数
 * @returns 选品候选分页列表
 */
export const getProductPickPage = (params: any) => request.get('/products/picks', { params });

/**
 * 申请快速寄样
 *
 * 为指定商品关联关系发起快速寄样流程。
 *
 * @param relationId - 商品关联 ID
 * @param data - 寄样申请参数
 * @returns 申请结果
 */
export const applyQuickSample = (relationId: string, data: any) =>
  request.post(`/products/${relationId}/quick-sample`, data);
/**
 * 分页查询合作伙伴列表
 *
 * @param params.keyword - 搜索关键词
 * @param params.type - 合作伙伴类型
 * @param params.page - 页码
 * @param params.size - 每页数量
 * @returns 合作伙伴分页列表
 */
export const listPartners = (params: { keyword?: string; type?: string; page?: number; size?: number }) =>
  request.get('/colonel/partners', { params });

/**
 * 获取合作伙伴详情
 * @param id - 合作伙伴 ID
 * @param params.type - 合作伙伴类型（可选）
 * @returns 合作伙伴详细信息
 */
export const getPartnerDetail = (id: string | number, params?: { type?: string }) =>
  request.get(`/colonel/partners/${id}`, { params });

/**
 * 查询合作伙伴关联商品
 *
 * @param id - 合作伙伴 ID
 * @param params.type - 合作伙伴类型
 * @param params.page - 页码
 * @param params.size - 每页数量
 * @returns 合作伙伴商品分页列表
 */
export const getPartnerProducts = (
  id: string | number,
  params?: { type?: string; page?: number; size?: number }
) => request.get(`/colonel/partners/${id}/products`, { params });
