/**
 * @module api/talent
 * @description 达人 CRM API 模块
 *
 * 负责达人（Talent）全生命周期管理，是「达人 CRM」前端页面的核心数据层。
 * 涵盖能力包括：
 * - 达人池管理：公海 / 私海池的查询、认领（claim）、释放（release）
 * - 达人资料：详情查询、资料解析（resolve）、同步（sync）、手动补全
 * - 黑名单：加入 / 移除黑名单
 * - 标签管理：预设标签查询、达人标签更新
 * - 收货地址：查询 / 更新达人收货地址
 * - 批量导入：通过抖音账号列表批量导入达人
 * - 状态流转：查询达人状态机配置（认领保护期、多认领开关等）
 * - 排除检查：检查达人是否为独家达人
 * - 刷新任务：触发达人数据刷新（单个 / 每周全量）
 *
 * 核心业务概念：
 * - 公海池（public pool）：未被认领的达人，所有业务人员可见可认领
 * - 私海池（private pool）：已被认领的达人，仅认领人可见
 * - 保护期（protectedUntil）：认领后的独占保护时间窗口
 * - 多认领（allowMultiClaim）：同一达人是否可被多人同时认领
 * - 数据源（dataSource）：达人信息的来源（抖音爬虫 / 手动补全等）
 */
import request from '../utils/request';

/**
 * 达人查询参数
 *
 * 支持 18+ 个维度的筛选条件，用于达人列表页的高级搜索。
 */
export interface TalentQueryParams {
  page?: number
  size?: number
  keyword?: string
  view?: string
  category?: string
  claimStatus?: string
  minFans?: number
  maxFans?: number
  region?: string
  poolStatus?: string
  ownerKeyword?: string
  douyinNo?: string
  nickname?: string
  liveSalesBand?: string
  liveViewBand?: string
  liveGpmBand?: string
  videoSalesBand?: string
  videoPlayBand?: string
  videoGpmBand?: string
  level?: string
  gender?: string
  contactStatus?: string
}

/**
 * 达人列表项数据结构
 *
 * 对应达人列表页每行显示的完整字段集，含抖音基础信息、
 * CRM 管理信息（池状态、归属人、保护期）和运营指标。
 */
export interface TalentListItem {
  id: string
  nickname?: string | null
  douyinUid?: string | null
  douyinNo?: string | null
  uid?: string | null
  fansCount?: number | null
  likesCount?: number | null
  worksCount?: number | null
  ipLocation?: string | null
  level?: string | null
  monthlySales?: number | null
  poolStatus?: string | null
  ownerId?: string | null
  ownerName?: string | null
  protectedUntil?: string | null
  sampleCount?: number | null
  orderCount?: number | null
  serviceFeeContribution?: number | null
  mainCategory?: string | null
  liveSalesBand?: string | null
  liveViewBand?: string | null
  liveGpmBand?: string | null
  videoSalesBand?: string | null
  videoPlayBand?: string | null
  videoGpmBand?: string | null
  blacklistReason?: string | null
  contactPhone?: string | null
  remark?: string | null
  avatarUrl?: string | null
  claimedAt?: string | null
  blacklisted?: boolean | null
  naturalOrderTalent?: boolean | null
  activeClaimCount?: number | null
  claimTags?: string[] | null
}

/**
 * 达人资料数据
 *
 * 用于达人资料解析（resolve）和同步（sync）的返回结构，
 * 包含抖音账号基础信息和运营数据。
 */
export interface TalentProfilePayload {
  douyinAccount?: string | null
  talentUid?: string | null
  nickname?: string | null
  avatarUrl?: string | null
  fansCount?: number | null
  likeCount?: number | null
  followingCount?: number | null
  worksCount?: number | null
  ipLocation?: string | null
  talentLevel?: string | null
  sales30d?: number | null
}

/**
 * 达人资料解析响应
 *
 * 解析操作可能成功也可能部分成功（如某些字段不支持），
 * 通过 success 和 syncStatus 字段标识整体结果。
 */
export interface ResolveTalentProfileResponse {
  success: boolean
  provider?: string | null
  syncStatus?: string | null
  profile?: TalentProfilePayload | null
  unsupportedFields?: string[]
  rawPayloadSaved?: boolean
  dataSource?: string | null
  syncErrorCode?: string | null
  syncErrorMessage?: string | null
}

/**
 * 达人详情完整响应
 *
 * 达人详情页的核心数据结构，包含四个子对象：
 * - talent：达人基础信息、运营指标、同步状态
 * - claim：认领状态、归属人、保护期、多认领信息
 * - samples：关联寄样记录列表
 * - orders：关联订单记录列表
 */
export interface TalentDetailResponse {
  talent?: {
    id?: string
    nickname?: string | null
    douyinUid?: string | null
    douyinNo?: string | null
    uid?: string | null
    profileUrl?: string | null
    fansCount?: number | null
    likesCount?: number | null
    worksCount?: number | null
    ipLocation?: string | null
    level?: string | null
    monthlySales?: number | null
    mainCategory?: string | null
    liveSalesBand?: string | null
    liveViewBand?: string | null
    liveGpmBand?: string | null
    videoSalesBand?: string | null
    videoPlayBand?: string | null
    videoGpmBand?: string | null
    blacklisted?: boolean | null
    blacklistReason?: string | null
    orderCount?: number | null
    sampleCount?: number | null
    serviceFeeContribution?: number | null
    contactPhone?: string | null
    remark?: string | null
    avatarUrl?: string | null
    tags?: string[] | null
    tagUpdatedBy?: string | null
    shippingRecipientName?: string | null
    shippingRecipientPhone?: string | null
    shippingRecipientAddress?: string | null
    claimTags?: string[] | null
    dataSource?: string | null
    syncStatus?: string | null
    unsupportedFields?: string[]
    syncErrorMessage?: string | null
    talentLevel?: string | null
    sales30d?: number | null
  }
  claim?: {
    poolStatus?: string | null
    ownerId?: string | null
    ownerName?: string | null
    claimedAt?: string | null
    protectedUntil?: string | null
    lastOrderAt?: string | null
    recipientName?: string | null
    recipientPhone?: string | null
    recipientAddress?: string | null
    activeClaimCount?: number | null
  activeClaimOwners?: Array<{
    userId?: string | null
    ownerName?: string | null
    claimedAt?: string | null
    protectedUntil?: string | null
  }>
  claimStatus?: string | null
  }
  samples?: Array<{
    sampleRequestId?: string | null
    productName?: string | null
    status?: string | null
    statusText?: string | null
    createTime?: string | null
    completeTime?: string | null
  }>
  orders?: Array<{
    orderId?: string | null
    productName?: string | null
    orderAmount?: number | null
    serviceFee?: number | null
    channelName?: string | null
    createTime?: string | null
  }>
}

/**
 * 达人状态流转配置响应
 *
 * 定义达人 CRM 的状态机模型，前端根据此配置动态渲染操作按钮和状态标签。
 * - states：所有可能的达人状态及其可见角色
 * - transitions：状态间的转换规则（起始状态 -> 动作 -> 目标状态）
 */
export interface TalentStatusTransitionsResponse {
  protectionConfigKey: string
  allowMultiClaim: boolean
  states: Array<{
    code: string
    label: string
    description: string
    canClaim: boolean
    canRelease: boolean
    visibleToRoles: string[]
  }>
  transitions: Array<{
    fromState: string
    action: string
    actionLabel: string
    toState: string
    actorRoles: string[]
    condition: string
    effect: string
  }>
}

// ==================== 达人池查询 ====================

/**
 * 分页查询达人列表（主列表，含全部筛选维度）
 *
 * @param params - 查询参数（TalentQueryParams）
 * @returns 达人分页列表
 */
export const getTalentPage = (params: TalentQueryParams) => request.get('/talents', { params });

/**
 * 查询公海达人池
 *
 * 公海池包含未被认领或已释放的达人，所有业务人员均可查看和认领。
 *
 * @param params - 筛选参数
 * @returns 公海达人分页列表
 */
export const getTalentPublic = (params: any) => request.get('/talents/pools/public', { params });

/**
 * 查询私海达人池
 *
 * 私海池包含当前用户已认领的达人，仅认领人可见。
 *
 * @param params - 可选的筛选参数
 * @returns 私海达人列表
 */
export const getTalentPrivate = (params?: Record<string, unknown>) =>
  request.get('/talents/pools/private', { params });

/**
 * 按渠道人员查询私海达人池（管理员专用）
 *
 * 管理员在快速寄样时选择指定渠道后，查询该渠道人员认领的达人列表。
 *
 * @param channelUserId - 渠道人员用户 ID
 * @returns 指定渠道人员已认领的私海达人列表
 */
export const getTalentByChannel = (channelUserId: string) =>
  request.get(`/talents/pools/by-channel/${channelUserId}`).then((res: any) => res.data);

/** 私海接口返回 data 为数组（非分页 records），统一解析为列表 */
export function parsePrivateTalentPoolResponse(res: { data?: unknown } | null | undefined) {
  const data = res?.data
  if (Array.isArray(data)) return data
  if (data && typeof data === 'object' && Array.isArray((data as { records?: unknown[] }).records)) {
    return (data as { records: unknown[] }).records
  }
  return []
}

/**
 * 将私海达人数据转换为下拉选择器选项格式
 *
 * label 优先级：昵称 > 抖音号 > 抖音 UID，
 * 若有抖音号则附加展示（如 "张三（dy123）"）。
 *
 * @param item - 私海达人原始数据
 * @returns { label: string, value: string } 选择器选项
 */
export function toPrivateTalentSelectOption(item: Record<string, unknown>) {
  const nickname = String(item?.nickname || item?.talentName || '').trim()
  const douyinUid = String(item?.douyinUid || item?.uid || item?.talentId || '').trim()
  const douyinNo = String(item?.douyinNo || '').trim()
  const labelBase = nickname || douyinNo || douyinUid
  const label = douyinNo && labelBase !== douyinNo ? `${labelBase}（${douyinNo}）` : labelBase
  return {
    label,
    value: douyinUid
  }
}
/**
 * 获取达人状态流转配置
 * @returns 状态机配置（states + transitions）
 */
export const getTalentStatusTransitions = () => request.get('/talents/status-transitions');

/**
 * 获取达人详情
 *
 * 返回达人完整信息，包含基础资料、认领状态、寄样记录和订单记录。
 *
 * @param id - 达人 ID
 * @returns 达人详情（TalentDetailResponse）
 */
export const getTalentById = (id: string): Promise<TalentDetailResponse> =>
  request.get(`/talents/${id}`).then((res: any) => res.data as TalentDetailResponse);
/**
 * 解析达人资料
 *
 * 通过抖音号 / UID / 昵称等输入，从抖音开放平台解析达人资料。
 * 支持三种模式：
 * - 自动解析（默认）
 * - 强制刷新缓存（forceRefresh）
 * - 手动补全（manualFill + manualPayload）
 *
 * @param payload.input - 达人标识（抖音号、UID 或昵称）
 * @param payload.forceRefresh - 是否强制刷新缓存
 * @param payload.manualFill - 是否手动补全资料
 * @param payload.manualPayload - 手动补全的数据
 * @returns 解析结果（含资料数据和同步状态）
 */
export const resolveTalentProfile = (payload: {
  input: string
  forceRefresh?: boolean
  manualFill?: boolean
  manualPayload?: Record<string, unknown>
}) => request.post('/talents/resolve-profile', payload).then((res: any) => res.data as ResolveTalentProfileResponse);

/**
 * 同步已有达人的资料
 *
 * 与 resolveTalentProfile 不同，此接口针对已入库的达人，
 * 通过达人 ID 触发资料更新。
 *
 * @param id - 达人 ID
 * @param forceRefresh - 是否强制刷新，默认 false
 * @returns 同步结果
 */
export const syncTalentProfile = (id: string, forceRefresh = false) =>
  request.post(`/talents/${id}/sync-profile`, null, { params: { forceRefresh } }).then((res: any) => res.data as ResolveTalentProfileResponse);
/** 创建达人 @param data - 达人信息 */
export const createTalent = (data: any) => request.post('/talents', data);
/** 更新达人信息 @param id - 达人 ID @param data - 更新字段 */
export const updateTalent = (id: string, data: any) => request.put(`/talents/${id}`, data);

/**
 * 更新达人标签
 *
 * 曾换式更新：新标签列表完全覆盖旧标签。
 *
 * @param id - 达人 ID
 * @param tags - 新标签列表
 * @returns 更新后的标签列表
 */
export const updateTalentTags = (id: string, tags: string[]) =>
  request.put(`/talents/${id}/tags`, { tags }).then((res: any) => res.data as string[]);

/**
 * 更新达人收货地址
 * @param id - 达人 ID
 * @param payload - 收货地址信息（收件人、电话、地址）
 */
export const updateTalentShippingAddress = (
  id: string,
  payload: { recipientName?: string; recipientPhone?: string; recipientAddress?: string }
) => request.put(`/talents/${id}/shipping-address`, payload).then((res: any) => res.data);
/**
 * 查询达人收货地址
 * @param id - 达人 ID
 * @returns 收货地址信息
 */
export const getTalentShippingAddress = (id: string) =>
  request.get(`/talents/${id}/shipping-address`).then((res: any) => res.data as {
    recipientName?: string | null
    recipientPhone?: string | null
    recipientAddress?: string | null
  });
/**
 * 批量导入达人
 *
 * 通过抖音账号列表批量创建达人记录，后端自动解析资料。
 *
 * @param accounts - 抖音账号列表（抖音号或 UID）
 * @returns 导入结果（成功/失败数量）
 */
export const batchImportTalents = (accounts: string[]) =>
  request.post('/talents/batch-import', { accounts }).then((res: any) => res.data);

/**
 * 获取预设达人标签列表
 * @returns 预设标签数组
 */
export const getPresetTalentTags = () =>
  request.get('/talents/preset-tags').then((res: any) => res.data as string[]);

/** 删除达人 @param id - 达人 ID */
export const deleteTalent = (id: string) => request.delete(`/talents/${id}`);

/**
 * 认领达人（加入私海）
 *
 * @param id - 达人 ID
 * @returns 认领结果（含保护期信息）
 */
export const claimTalent = (id: string) => request.post(`/talents/${id}/claims`);

/**
 * 释放达人（退回公海）
 *
 * @param id - 达人 ID
 * @returns 释放结果
 */
export const releaseTalent = (id: string) => request.post(`/talents/${id}/release`);

/**
 * 将达人加入黑名单
 *
 * @param id - 达人 ID
 * @param data.reason - 黑名单原因（可选）
 */
export const blacklistTalent = (id: string, data?: { reason?: string }) => request.post(`/talents/${id}/blacklist`, data || {});

/**
 * 将达人从黑名单移除
 * @param id - 达人 ID
 */
export const unblacklistTalent = (id: string) => request.post(`/talents/${id}/unblacklist`);

/**
 * 刷新单个达人数据
 *
 * 触发后端重新从抖音平台拉取达人最新数据。
 *
 * @param id - 达人 ID
 * @returns 刷新任务结果
 */
export const refreshTalent = (id: string) => request.post(`/talents/${id}/refresh`);

/**
 * 触发每周达人数据全量刷新
 *
 * 管理员操作，批量更新所有活跃达人的数据。
 *
 * @returns 全量刷新任务结果
 */
export const refreshWeeklyTalents = () => request.post('/talents/refresh/weekly');

/**
 * 手动补全达人资料
 *
 * 当自动解析失败或字段缺失时，手动填写达人资料。
 *
 * @param id - 达人 ID
 * @param data - 手动填写的资料数据
 * @returns 更新结果
 */
export const manualFillTalent = (id: string, data: any) => request.put(`/talents/${id}/manual-fill`, data);

/**
 * 查询达人最新的资料丰富任务
 *
 * @param id - 达人 ID
 * @returns 最新 enrich 任务状态和结果
 */
export const getLatestEnrichTask = (id: string) => request.get(`/talents/${id}/enrich-task/latest`);

/**
 * 检查达人是否为独家达人
 *
 * 独家达人有特殊的合作规则和保护机制。
 *
 * @param id - 达人 ID
 * @returns 独家状态信息
 */
export const exclusiveCheck = (id: string) => request.get(`/talents/${id}/exclusive-status`);
