/** 商品列表筛选口径（前端聚合；部分维度依赖已加载列表做客户端过滤） */

export interface ProductFilterState {
  /** 系统人工标签（原 category 字段） */
  systemTag: string | null
  commission: string | null
  hasSample: string | null
  assignee: string | null
  decision: string | null
  /** 店铺 / 合作方名称模糊匹配 */
  shopKeyword: string | null
  /** 抖音同步类目（categoryName） */
  categoryName: string | null
  /** 近 30 天销量区间 */
  salesRange: string | null
  /** 系统转链状态 */
  promotionLink: string | null
  /** 联盟侧推广状态文案（statusText） */
  allianceStatus: string | null
}

export const DEFAULT_PRODUCT_FILTERS = (): ProductFilterState => ({
  systemTag: null,
  commission: null,
  hasSample: null,
  assignee: null,
  decision: null,
  shopKeyword: null,
  categoryName: null,
  salesRange: null,
  promotionLink: null,
  allianceStatus: null
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

/** 常见抖音类目（可与列表中的 categoryName 叠加使用） */
export const categoryNameOptions = [
  { label: '玩具乐器', value: '玩具' },
  { label: '智能家居', value: '家居' },
  { label: '美妆护肤', value: '美妆' },
  { label: '食品饮料', value: '食品' },
  { label: '服饰内衣', value: '服饰' },
  { label: '母婴宠物', value: '母婴' },
  { label: '数码家电', value: '数码' }
]

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
    if (!matchSalesRange(item, filters.salesRange)) return false
    if (!matchPromotionLink(item, filters.promotionLink)) return false
    if (!matchAllianceStatus(item, filters.allianceStatus)) return false
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
