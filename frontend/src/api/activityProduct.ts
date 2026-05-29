/**
 * @module api/activityProduct
 * @description 活动商品管理 API 模块
 *
 * 负责活动维度下的商品全生命周期管理，包括：
 * - 商品查询与详情（SKU 明细）
 * - 审核结果录入（审核通过 / 驳回及补充信息）
 * - 负责人分配（单个 / 批量）和审核人指定
 * - 商品决策分级（主推 / 次推 / 暂停 / 放弃）
 * - 推广链接生成（转链）
 * - 商品入库（加入共享商品库）
 * - 置顶 / 取消置顶
 * - 跟进记录管理
 * - 操作日志查询
 * - 从抖音同步活动商品
 *
 * 所有接口路径均以 `/colonel/activities/{activityId}/products` 为前缀，
 * 表示在特定活动上下文下的商品操作。
 */
import type { AxiosRequestConfig } from 'axios';
import request from '../utils/request';

/**
 * 分页获取活动商品列表
 *
 * @param activityId - 活动 ID
 * @param params - 查询参数（分页、筛选条件）
 * @param config - axios 请求配置
 * @returns 活动商品分页列表
 */
export const getActivityProducts = (activityId: string | number, params: any, config: any = {}) =>
  request.get(`/colonel/activities/${activityId}/products`, { params, ...config });

/**
 * 获取单个活动商品详情
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @returns 商品详情（含审核状态、决策分级、负责人等）
 */
export const getActivityProductDetail = (activityId: string | number, productId: string | number) =>
  request.get(`/colonel/activities/${activityId}/products/${productId}`);

/**
 * 获取活动商品的 SKU 列表
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @returns SKU 明细列表（含价格、库存等）
 */
export const getActivityProductSkus = (activityId: string | number, productId: string | number) =>
  request.get(`/colonel/activities/${activityId}/products/${productId}/skus`);

/**
 * 提交活动商品审核结果
 *
 * 录入审核通过或驳回的决策，并可附带详细信息：
 * 独家价格备注、运费信息、卖点、推广话术、是否支持投广及规则、
 * 奖励备注、参与要求、活动时间备注、素材文件、商品标签、
 * 以及寄样门槛条件（销量/等级/备注）。
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data - 审核结果数据
 * @param data.approved - 是否通过审核
 * @param data.reason - 驳回原因（驳回时必填）
 * @param data.exclusivePriceRemark - 独家价格备注
 * @param data.shippingInfo - 运费说明
 * @param data.sellingPoints - 卖点列表
 * @param data.promotionScript - 推广话术
 * @param data.supportsAds - 是否支持投广
 * @param data.adsRule - 投广规则说明
 * @param data.rewardRemark - 奖励备注
 * @param data.participationRequirements - 参与要求
 * @param data.campaignTimeRemark - 活动时间备注
 * @param data.materialFiles - 素材文件列表
 * @param data.goodsTags - 商品标签
 * @param data.productTags - 产品标签
 * @param data.sampleThresholdSales - 寄样门槛-销量
 * @param data.sampleThresholdLevel - 寄样门槛-等级
 * @param data.sampleThresholdRemark - 寄样门槛备注
 * @returns 更新结果
 */
export const auditActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: {
    approved: boolean;
    reason?: string;
    exclusivePriceRemark?: string;
    shippingInfo?: string;
    sellingPoints?: string[];
    promotionScript?: string;
    supportsAds?: boolean;
    adsRule?: string;
    rewardRemark?: string;
    participationRequirements?: string;
    campaignTimeRemark?: string;
    materialFiles?: string[];
    goodsTags?: string[];
    productTags?: string[];
    sampleThresholdSales?: number;
    sampleThresholdLevel?: number;
    sampleThresholdRemark?: string;
  }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/audit-result`, data);

/**
 * 分配活动商品招商组长
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data.assigneeId - 负责人用户 ID
 * @returns 分配结果
 */
export const assignActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: { assigneeId: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/assignee`, data);

/**
 * 批量分配活动商品招商组长
 *
 * @param activityId - 活动 ID
 * @param data.productIds - 商品 ID 列表
 * @param data.assigneeId - 负责人用户 ID
 * @returns 批量分配结果
 */
export const batchAssignActivityProducts = (
  activityId: string | number,
  data: { productIds: string[]; assigneeId: string }
) => request.post(`/colonel/activities/${activityId}/products/batch-assign`, data);

/**
 * 指定活动商品审核人
 *
 * 与负责人分配不同，审核人负责审核决策的操作权限。
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data.assigneeId - 审核人用户 ID
 * @returns 指定结果
 */
export const assignActivityProductAuditOwner = (
  activityId: string | number,
  productId: string | number,
  data: { assigneeId: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/audit-assignee`, data);

/**
 * 更新活动商品决策分级
 *
 * 决策分级决定商品在运营策略中的优先级：
 * - MAIN：主推品，优先资源倾斜
 * - SECONDARY：次推品，常规跟进
 * - PAUSE：暂停推广，临时搁置
 * - DROP：放弃，停止运营
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data.decisionLevel - 决策等级
 * @param data.reason - 决策原因说明
 * @returns 更新结果
 */
export const updateActivityProductDecision = (
  activityId: string | number,
  productId: string | number,
  data: { decisionLevel: 'MAIN' | 'SECONDARY' | 'PAUSE' | 'DROP'; reason: string }
) => request.put(`/colonel/activities/${activityId}/products/${productId}/decision`, data);

/**
 * 生成活动商品推广链接（转链）
 *
 * 为商品生成带追踪参数的推广链接，用于达人分享或商品库展示。
 * scene 参数决定链接的使用场景，影响后端生成的链接参数配置。
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data.scene - 使用场景：PRODUCT_LIBRARY(商品库) / PRODUCT_DETAIL(商品详情) / TALENT_SHARE(达人分享) / SAMPLE_DESK(寄样台)
 * @param data.talentId - 达人分享场景时的目标达人 ID
 * @returns 推广链接信息
 */
export const convertActivityProductLink = (
  activityId: string | number,
  productId: string | number,
  data?: { scene?: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK'; talentId?: string },
  config?: AxiosRequestConfig & { suppressErrorNotice?: boolean }
) => request.post(`/colonel/activities/${activityId}/products/${productId}/promotion-links`, data || { scene: 'PRODUCT_LIBRARY' }, config);

/**
 * 将单个活动商品加入共享商品库
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @returns 入库结果
 */
export const putActivityProductIntoLibrary = (activityId: string | number, productId: string | number) =>
  request.post(`/colonel/activities/${activityId}/products/${productId}/library-entry`);

/**
 * 批量将活动商品加入共享商品库
 *
 * @param activityId - 活动 ID
 * @param data.productIds - 商品 ID 列表
 * @returns 批量入库结果
 */
export const batchPutActivityProductsIntoLibrary = (
  activityId: string | number,
  data: { productIds: string[] }
) => request.post(`/colonel/activities/${activityId}/products/batch-library-entry`, data);

/**
 * 置顶活动商品
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @returns 置顶结果
 */
export const pinActivityProduct = (activityId: string | number, productId: string | number) =>
  request.post(`/colonel/activities/${activityId}/products/${productId}/pin`);

/**
 * 取消置顶活动商品
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @returns 取消置顶结果
 */
export const unpinActivityProduct = (activityId: string | number, productId: string | number) =>
  request.delete(`/colonel/activities/${activityId}/products/${productId}/pin`);

/**
 * 批量置顶活动商品
 *
 * @param activityId - 活动 ID
 * @param data.productIds - 商品 ID 列表
 * @returns 批量置顶结果
 */
export const batchPinActivityProducts = (
  activityId: string | number,
  data: { productIds: string[] }
) => request.post(`/colonel/activities/${activityId}/products/batch-pin`, data);

/**
 * 获取当前用户置顶的商品列表
 *
 * @returns 置顶商品数组
 */
export const getPinnedProducts = () =>
  request.get('/colonel/pinned-products').then((res: any) => res.data);

/**
 * 记录活动商品跟进操作
 *
 * 记录运营人员对商品的跟进情况，包括跟进状态、内容和下次跟进时间。
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param data.talentId - 关联达人 ID（可选）
 * @param data.talentName - 关联达人名称（可选）
 * @param data.followStatus - 跟进状态
 * @param data.content - 跟进内容描述
 * @param data.nextFollowTime - 下次跟进时间
 * @param data.operatorName - 操作人名称
 * @returns 跟进记录结果
 */
export const followActivityProduct = (
  activityId: string | number,
  productId: string | number,
  data: {
    talentId?: string;
    talentName?: string;
    followStatus: string;
    content?: string;
    nextFollowTime?: string;
    operatorName?: string;
  }
) => request.post(`/colonel/activities/${activityId}/products/${productId}/follow`, data);

/**
 * 分页查询活动商品操作日志
 *
 * 获取商品在活动中的所有操作记录（审核、分配、决策变更等）。
 *
 * @param activityId - 活动 ID
 * @param productId - 商品 ID
 * @param params - 分页参数
 * @returns 操作日志分页列表
 */
export const getActivityProductOperationLogs = (
  activityId: string | number,
  productId: string | number,
  params?: { page?: number; size?: number }
) => request.get(`/colonel/activities/${activityId}/products/${productId}/operation-logs`, { params });

/**
 * 从抖音同步活动商品
 *
 * 触发后端从抖音开放平台拉取指定活动下的最新商品数据。
 * 同步是异步操作，此接口仅发起同步请求。
 *
 * @param activityId - 活动 ID
 * @returns 同步任务结果
 */
export const syncActivityProducts = (activityId: string | number) =>
  request.post(`/colonel/activities/${activityId}/products/sync`);
