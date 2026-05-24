/** 商品列表筛选口径（前端聚合；商品库主链路走后端 GET /products 服务端过滤） */

/** @deprecated 仅测试/历史兼容；商品库类目请使用 GET /products/categories 动态加载，禁止作为主数据来源 */
export const libraryCategoryOptionsPreset: { label: string; value: string }[] = []

export interface ProductFilterState {
  /** 系统人工标签（原 category 字段） */
  systemTag: string | null
  commission: string | null
  hasSample: string | null
  assignee: string | null
  decision: string | null
  /** 店铺 / 合作方名称模糊匹配 */
  shopKeyword: string | null
  /** 抖音同步类目（单选兼容，活动商品页） */
  categoryName: string | null
  /** 商品库类目多选 */
  categories: string[]
  /** 来源活动 ID */
  activityId: string | null
  /** 招商负责人用户 ID */
  assigneeId: string | null
  /** 服务费率区间 */
  serviceFee: string | null
  /** 是否支持投流 */
  supportsAds: string | null
  /** 近 30 天销量区间 */
  salesRange: string | null
  /** 系统转链状态 */
  promotionLink: string | null
  /** 联盟侧推广状态文案（statusText） */
  allianceStatus: string | null
  /** 审核补充信息中的货品标签 */
  goodsTags: string[]
  /** 审核补充信息中的商品标签 */
  productTags: string[]
  /** 团长名称 */
  colonelName: string | null
  /** 已发布转链 */
  published: string | null
  /** 联盟侧已挂车/推广中（upstream status=1） */
  listed: string | null
  /** 免费寄样（supplement sampleType=FREE） */
  freeSample: string | null
  /** 合作类型 */
  cooperationType: string | null
  /** 直播价格下限 */
  livePriceMin: string | null
  /** 直播价格上限 */
  livePriceMax: string | null
  /** 佣金率下限 */
  commissionMin: string | null
  /** 佣金率上限 */
  commissionMax: string | null
  /** 寄样销售额下限 */
  sampleSalesMin: string | null
  /** 寄样销售额上限 */
  sampleSalesMax: string | null
  /** 其他筛选 Checkbox（AND） */
  materialDownload: boolean
  exclusivePrice: boolean
  productChain: boolean
  handCard: boolean
  doubleCommission: boolean
  /** 仅未加入货盘 */
  notInLibrary: boolean
  /** 选品去重 */
  dedup: boolean
  /** 招商活动 ID */
  recruitActivityId: string | null
  /** 招商活动名称关键字 */
  recruitActivityName: string | null
}

export const DEFAULT_PRODUCT_FILTERS = (): ProductFilterState => ({
  systemTag: null,
  commission: null,
  hasSample: null,
  assignee: null,
  decision: null,
  shopKeyword: null,
  categoryName: null,
  categories: [],
  activityId: null,
  assigneeId: null,
  serviceFee: null,
  supportsAds: null,
  salesRange: null,
  promotionLink: null,
  allianceStatus: null,
  goodsTags: [],
  productTags: [],
  colonelName: null,
  published: null,
  listed: null,
  freeSample: null,
  cooperationType: null,
  livePriceMin: null,
  livePriceMax: null,
  commissionMin: null,
  commissionMax: null,
  sampleSalesMin: null,
  sampleSalesMax: null,
  materialDownload: false,
  exclusivePrice: false,
  productChain: false,
  handCard: false,
  doubleCommission: false,
  notInLibrary: false,
  dedup: false,
  recruitActivityId: null,
  recruitActivityName: null
})

export const systemTagOptions = [
  { label: '高佣爆款', value: 'high_commission' },
  { label: '适合投放', value: 'traffic' },
  { label: '新品首发', value: 'new' },
  { label: '高客单价', value: 'high_price' }
]

export const commissionOptions = [
  { label: '20%以上', value: 'gt20' },
  { label: '10% - 20%', value: '10_20' },
  { label: '10%以下', value: 'lt10' }
]

export const yesNoOptions = [
  { label: '是', value: '1' },
  { label: '否', value: '0' }
]

export const assigneeOptions = [
  { label: '已分配负责人', value: 'assigned' },
  { label: '未分配负责人', value: 'unassigned' }
]

export const decisionOptions = [
  { label: '主推', value: 'MAIN' },
  { label: '次推', value: 'SECONDARY' },
  { label: '暂缓', value: 'PAUSE' },
  { label: '放弃', value: 'DROP' },
  { label: '暂无判断', value: 'NONE' }
]

export const bizStatusOptions = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '审核通过', value: 'APPROVED' },
  { label: '审核拒绝', value: 'REJECTED' },
  { label: '历史已绑定', value: 'BOUND' },
  { label: '已分配招商', value: 'ASSIGNED' },
  { label: '已转链', value: 'LINKED' },
  { label: '已转交达人 CRM', value: 'FOLLOWING' }
]

export const serviceFeeOptions = [
  { label: '20%以上', value: 'gt20' },
  { label: '10% - 20%', value: '10_20' },
  { label: '10%以下', value: 'lt10' }
]

export const supportsAdsOptions = [
  { label: '支持投流', value: '1' },
  { label: '不支持投流', value: '0' }
]

/** 商品域预设 22 类目（§2.8），与 GET /products/categories 动态结果合并使用 */
export const productDomainCategoryOptions: { label: string; value: string }[] = [
  { label: '玩具乐器', value: '玩具乐器' },
  { label: '服饰内衣', value: '服饰内衣' },
  { label: '个护家清', value: '个护家清' },
  { label: '智能家居', value: '智能家居' },
  { label: '生鲜', value: '生鲜' },
  { label: '美妆', value: '美妆' },
  { label: '母婴宠物', value: '母婴宠物' },
  { label: '鲜花园艺', value: '鲜花园艺' },
  { label: '本地生活', value: '本地生活' },
  { label: '食品饮料', value: '食品饮料' },
  { label: '3C数码家电', value: '3C数码家电' },
  { label: '图书教育', value: '图书教育' },
  { label: '鞋靴箱包', value: '鞋靴箱包' },
  { label: '虚拟充值', value: '虚拟充值' },
  { label: '运动户外', value: '运动户外' },
  { label: '钟表配饰', value: '钟表配饰' },
  { label: '珠宝文玩', value: '珠宝文玩' },
  { label: '医疗健康', value: '医疗健康' },
  { label: '酒类', value: '酒类' },
  { label: '滋补保健', value: '滋补保健' },
  { label: '原料包装', value: '原料包装' },
  { label: '餐饮外卖', value: '餐饮外卖' }
]

/** 类目名称筛选（活动商品页）；与 productDomainCategoryOptions 对齐 */
export const categoryNameOptions = productDomainCategoryOptions

export const salesRangeOptions = [
  { label: '近30天销量 < 100', value: 'lt100' },
  { label: '100 - 999', value: '100_999' },
  { label: '1000 - 29999', value: '1k_29k' },
  { label: '≥ 30000（寄样门槛）', value: 'gte30000' }
]

export const promotionLinkOptions = [
  { label: '未生成转链', value: 'PENDING' },
  { label: '已生成转链', value: 'LINKED' },
  { label: '转链失败', value: 'FAILED' }
]

export const goodsTagOptions = [
  { label: '家居', value: '家居' },
  { label: '零食', value: '零食' },
  { label: '美妆', value: '美妆' },
  { label: '母婴', value: '母婴' },
  { label: '服饰', value: '服饰' },
  { label: '数码', value: '数码' },
  { label: '高复购', value: '高复购' },
  { label: '新品', value: '新品' }
]

export const productTagOptions = [
  { label: '主推', value: '主推' },
  { label: '次推', value: '次推' },
  { label: '商品链组', value: '商品链组' },
  { label: '同款高价', value: '同款高价' },
  { label: '全网低价', value: '全网低价' },
  { label: '手卡', value: '手卡' },
  { label: '专属价', value: '专属价' },
  { label: '双佣金', value: '双佣金' }
]

/** 联盟推广状态码（与抖店 upstream status / product_snapshot.status 一致） */
export const allianceStatusToUpstreamStatus: Record<string, number> = {
  pending_audit: 0,
  promoting: 1,
  rejected: 2,
  terminated: 3,
  expired: 6
}

/** 联盟推广状态（匹配 statusText 关键字；无 upstream 码时用客户端兜底） */
export const allianceStatusOptions = [
  { label: '推广中', value: 'promoting' },
  { label: '待审核', value: 'pending_audit' },
  { label: '申请未通过', value: 'rejected' },
  { label: '合作已终止', value: 'terminated' },
  { label: '合作已过期', value: 'expired' }
]

const parsePercent = (value?: string | number | null) =>
  Number(String(value ?? '').replace('%', '').trim()) || 0

const parsePrice = (value?: string | number | null) =>
  Number(String(value ?? '').replace(/[^\d.]/g, '')) || 0

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const hasPromotionLink = (item: any) =>
  Boolean(normalizeText(item?.promotion?.link || item?.promotionLink || item?.promoteLink || item?.shortLink))

const resolvePromotionStatus = (item: any) => {
  const explicitStatus = normalizeText(item?.promotion?.status || item?.promotionLinkStatus).toUpperCase()
  if (explicitStatus === 'FAILED') return 'FAILED'
  if (explicitStatus === 'LINKED' || explicitStatus === 'READY' || explicitStatus === 'SUCCESS') return 'LINKED'
  if (hasPromotionLink(item)) return 'LINKED'
  return explicitStatus || 'PENDING'
}

export function matchSystemTag(item: any, systemTag: string | null) {
  if (!systemTag) return true
  const tags = Array.isArray(item?.systemTags) ? item.systemTags : []
  if (systemTag === 'high_commission') return tags.includes('高佣')
  if (systemTag === 'traffic') return tags.includes('抖音商品池')
  if (systemTag === 'new') return Number(item?.sales30d ?? item?.sales ?? 0) < 100
  if (systemTag === 'high_price') return parsePrice(item?.priceText) >= 300
  return true
}

export function matchCommission(item: any, commission: string | null) {
  if (!commission) return true
  const rate = parsePercent(item?.activityCosRatioText)
  if (commission === 'gt20') return rate >= 20
  if (commission === '10_20') return rate >= 10 && rate < 20
  if (commission === 'lt10') return rate < 10
  return true
}

export function matchHasSample(item: any, hasSample: string | null) {
  if (!hasSample) return true
  return hasSample === '1' ? Boolean(item?.hasSampleRule) : !item?.hasSampleRule
}

export function matchAssignee(item: any, assignee: string | null) {
  if (!assignee) return true
  return assignee === 'assigned' ? Boolean(item?.assigneeName) : !item?.assigneeName
}

export function matchDecision(item: any, decision: string | null) {
  if (!decision) return true
  if (decision === 'NONE') return !item?.latestDecisionLevel
  return item?.latestDecisionLevel === decision
}

export function matchShopKeyword(item: any, shopKeyword: string | null) {
  if (!shopKeyword) return true
  const keyword = normalizeText(shopKeyword).toLowerCase()
  const shop = normalizeText(item?.shopName).toLowerCase()
  return shop.includes(keyword)
}

export function matchCategoryName(item: any, categoryName: string | null) {
  if (!categoryName) return true
  const name = normalizeText(item?.categoryName).toLowerCase()
  return name.includes(categoryName.toLowerCase())
}

export function matchSalesRange(item: any, salesRange: string | null) {
  if (!salesRange) return true
  const sales = Number(item?.sales30d ?? item?.sales ?? 0)
  if (salesRange === 'lt100') return sales < 100
  if (salesRange === '100_999') return sales >= 100 && sales < 1000
  if (salesRange === '1k_29k') return sales >= 1000 && sales < 30000
  if (salesRange === 'gte30000') return sales >= 30000
  return true
}

export function matchPromotionLink(item: any, promotionLink: string | null) {
  if (!promotionLink) return true
  return resolvePromotionStatus(item) === promotionLink
}

const allianceStatusKeywords: Record<string, string[]> = {
  promoting: ['推广中', '推广'],
  pending_audit: ['待审核', '审核中'],
  rejected: ['未通过', '拒绝', '申请未通过'],
  terminated: ['终止', '已终止'],
  expired: ['过期', '已过期']
}

export function matchAllianceStatus(item: any, allianceStatus: string | null) {
  if (!allianceStatus) return true
  const text = normalizeText(item?.statusText || item?.allianceStatusText).toLowerCase()
  const keywords = allianceStatusKeywords[allianceStatus] || []
  return keywords.some((word) => text.includes(word))
}

const normalizeTagList = (value: any): string[] => {
  if (Array.isArray(value)) return value.map((item) => normalizeText(item)).filter(Boolean)
  const text = normalizeText(value)
  return text ? text.split(/[,，\n]/).map((item) => normalizeText(item)).filter(Boolean) : []
}

const getAuditTags = (item: any, key: 'goodsTags' | 'productTags') => {
  const supplement = item?.auditSupplement || item?.auditSupplementSummary || {}
  const legacyKey = key === 'goodsTags' ? 'goods_tags' : 'product_tags'
  return normalizeTagList(supplement?.[key] ?? supplement?.[legacyKey] ?? item?.[key] ?? item?.[legacyKey])
}

export function matchAuditTags(item: any, goodsTags: string[] = [], productTags: string[] = []) {
  const expectedGoods = normalizeTagList(goodsTags)
  const expectedProducts = normalizeTagList(productTags)
  const matches = (actual: string[], expected: string[]) =>
    !expected.length || expected.some((tag) => actual.includes(tag))
  return matches(getAuditTags(item, 'goodsTags'), expectedGoods) &&
    matches(getAuditTags(item, 'productTags'), expectedProducts)
}

export type ProductLibraryQueryExtra = {
  page?: number
  size?: number
  keyword?: string
  status?: number | null
  partnerId?: string | null
  partnerType?: string | null
  sortBy?: string | null
}

/** 商品库 GET /products 查询参数，与后端 SelectedLibraryFilter 字段一一对应。 */
export function buildProductLibraryQueryParams(
  filters: ProductFilterState,
  extra: ProductLibraryQueryExtra = {}
) {
  const keyword = normalizeText(extra.keyword)
  const partnerId = normalizeText(extra.partnerId)
  const partnerType = normalizeText(extra.partnerType)
  return {
    page: extra.page,
    size: extra.size,
    keyword: keyword || undefined,
    status: extra.status ?? undefined,
    shopKeyword: filters.shopKeyword || undefined,
    categoryName: filters.categoryName || undefined,
    categories: filters.categories?.length ? filters.categories.join(',') : undefined,
    activityId: filters.activityId || undefined,
    assigneeId: filters.assigneeId || undefined,
    serviceFee: filters.serviceFee || undefined,
    supportsAds: filters.supportsAds || undefined,
    salesRange: filters.salesRange || undefined,
    promotionLink: filters.promotionLink || undefined,
    allianceStatus: filters.allianceStatus || undefined,
    commission: filters.commission || undefined,
    hasSample: filters.hasSample || undefined,
    assignee: filters.assignee || undefined,
    systemTag: filters.systemTag || undefined,
    decision: filters.decision || undefined,
    partnerId: partnerId || undefined,
    partnerType: partnerType || undefined,
    sortBy: extra.sortBy || undefined,
    goodsTags: filters.goodsTags?.length ? filters.goodsTags.join(',') : undefined,
    productTags: filters.productTags?.length ? filters.productTags.join(',') : undefined,
    colonelName: filters.colonelName || undefined,
    published: filters.published || undefined,
    listed: filters.listed || undefined,
    freeSample: filters.freeSample || undefined,
    cooperationType: filters.cooperationType || undefined,
    livePriceMin: filters.livePriceMin || undefined,
    livePriceMax: filters.livePriceMax || undefined,
    commissionMin: filters.commissionMin || undefined,
    commissionMax: filters.commissionMax || undefined,
    sampleSalesMin: filters.sampleSalesMin || undefined,
    sampleSalesMax: filters.sampleSalesMax || undefined,
    materialDownload: filters.materialDownload ? '1' : undefined,
    exclusivePrice: filters.exclusivePrice ? '1' : undefined,
    productChain: filters.productChain ? '1' : undefined,
    handCard: filters.handCard ? '1' : undefined,
    doubleCommission: filters.doubleCommission ? '1' : undefined,
    notInLibrary: filters.notInLibrary ? '1' : undefined,
    dedup: filters.dedup ? '1' : undefined,
    recruitActivityId: filters.recruitActivityId || undefined,
    recruitActivityName: filters.recruitActivityName || undefined
  }
}

export function applyProductFilters(
  items: any[],
  filters: ProductFilterState,
  bizStatus: string | null
) {
  return items.filter((item) => {
    if (bizStatus && item?.bizStatus !== bizStatus) return false
    if (!matchSystemTag(item, filters.systemTag)) return false
    if (!matchCommission(item, filters.commission)) return false
    if (!matchHasSample(item, filters.hasSample)) return false
    if (!matchAssignee(item, filters.assignee)) return false
    if (!matchDecision(item, filters.decision)) return false
    if (!matchShopKeyword(item, filters.shopKeyword)) return false
    if (!matchCategoryName(item, filters.categoryName)) return false
    if (filters.categories?.length && !filters.categories.some((category) => matchCategoryName(item, category))) return false
    if (!matchSalesRange(item, filters.salesRange)) return false
    if (!matchPromotionLink(item, filters.promotionLink)) return false
    if (!matchAllianceStatus(item, filters.allianceStatus)) return false
    if (!matchAuditTags(item, filters.goodsTags, filters.productTags)) return false
    return true
  })
}

/**
 * 活动商品远程搜索关键字（对应后端 productInfo）。
 * 优先级：下拉选中的商品 ID > 搜索框关键字 > 店铺关键字。
 * 不可把多段拼成空格串，否则后端 LIKE 无法命中（实测会返回 0 条）。
 */
export function buildActivityProductInfoQuery(
  selectedProduct: string | null,
  productKeyword: string,
  shopKeyword: string | null
) {
  const productId = normalizeText(selectedProduct)
  if (productId) return productId
  const keyword = normalizeText(productKeyword)
  if (keyword) return keyword
  const shop = normalizeText(shopKeyword)
  return shop || undefined
}

export function formatSales30d(item: any) {
  const sales = Number(item?.sales30d ?? item?.sales ?? 0)
  if (sales >= 10000) return `${(sales / 10000).toFixed(1)}万`
  return String(sales)
}

export function formatGmv30d(item: any) {
  const raw = item?.gmv30d
  if (raw === null || raw === undefined || raw === '') return '-'
  const num = Number(String(raw).replace(/[^\d.]/g, ''))
  if (!Number.isFinite(num) || num <= 0) return String(raw)
  if (num >= 10000) return `¥${(num / 10000).toFixed(2)}万`
  return `¥${num.toFixed(2)}`
}
