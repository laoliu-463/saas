export type ProductDisplayStatusCode = 'PENDING' | 'DISPLAYING' | 'HIDDEN'

/** 与后端 ProductDisplayRuleService 联盟侧「推广中」一致 */
export const PROMOTING_ALLIANCE_STATUS = 1

export type LibraryReadinessCode =
  | 'LIST_VISIBLE'
  | 'STORED_HIDDEN'
  | 'READY_AFTER_ENTRY'
  | 'BLOCKED_AFTER_ENTRY'
  | 'REJECTED'
  | 'PENDING_AUDIT'

export interface ProductLibraryDisplayView {
  selectedToLibrary: boolean
  libraryVisible: boolean
  displayStatus: ProductDisplayStatusCode | null
  displayStatusLabel: string
  hiddenReason: string | null
  hiddenReasonLabel: string | null
  entryLabel: string
  entryTagType: 'success' | 'warning' | 'default'
  listVisibilityLabel: string
  visibilityHint: string | null
  hiddenFromList: boolean
}

export interface ProductLibraryReadinessView {
  code: LibraryReadinessCode
  label: string
  tagType: 'success' | 'warning' | 'info' | 'default'
  hint: string | null
  /** 审核通过并入库后，是否满足商品库列表展示规则（联盟推广中 + 推广期有效） */
  canDisplayAfterEntry: boolean
}

const DISPLAY_STATUS_LABELS: Record<ProductDisplayStatusCode, string> = {
  PENDING: '待定',
  DISPLAYING: '展示中',
  HIDDEN: '已隐藏'
}

const HIDDEN_REASON_LABELS: Record<string, string> = {
  NOT_ELIGIBLE: '联盟商品状态不满足展示条件（需为「推广中」）',
  ACTIVITY_EXPIRED: '推广期已结束',
  REPLACED_BY_HIGHER_PRIORITY: '同商品已有更高优先级关系在商品库展示',
  REPLACED_BY_ADVANTAGE: '同商品已有更优条件的关系在商品库展示',
  ADMIN_FORCE_REPLACED: '展示关系已被管理员调整'
}

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

export function normalizeDisplayStatus(value?: string | null): ProductDisplayStatusCode | null {
  const code = normalizeText(value).toUpperCase()
  if (code === 'PENDING' || code === 'DISPLAYING' || code === 'HIDDEN') {
    return code
  }
  const mark = normalizeText(value).toUpperCase()
  if (mark === 'SHOWING') return 'DISPLAYING'
  if (mark === 'HIDDEN') return 'HIDDEN'
  return null
}

export function resolveHiddenReasonLabel(reason?: string | null) {
  const code = normalizeText(reason)
  if (!code) return null
  return HIDDEN_REASON_LABELS[code] || code
}

export function parsePromotionEndTime(value?: string | null): Date | null {
  const text = normalizeText(value)
  if (!text) return null
  const direct = new Date(text)
  if (!Number.isNaN(direct.getTime())) return direct
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    const endOfDay = new Date(`${text}T23:59:59`)
    return Number.isNaN(endOfDay.getTime()) ? null : endOfDay
  }
  return null
}

export function isPromotionExpired(item: any, now = new Date()) {
  if (Boolean(item?.activityExpired)) return true
  const end = parsePromotionEndTime(item?.promotionEndTime ?? item?.promotion_end_time)
  return end !== null && end.getTime() < now.getTime()
}

export function isPromotingAllianceStatus(item: any) {
  const status = item?.status
  if (status !== undefined && status !== null && status !== '') {
    return Number(status) === PROMOTING_ALLIANCE_STATUS
  }
  const text = normalizeText(item?.statusText).toLowerCase()
  return text.includes('推广中')
}

/** 入库后能否进入共享商品库列表（不含同品去重等运行时竞争） */
export function canDisplayInLibraryAfterEntry(item: any, now = new Date()) {
  if (Boolean(item?.manualDisabled)) return false
  if (isPromotionExpired(item, now)) return false
  return isPromotingAllianceStatus(item)
}

export function resolveLibraryEntryBlockReason(item: any, now = new Date()) {
  if (Boolean(item?.manualDisabled)) {
    return '商品已被手动停用，入库后不会在商品库展示'
  }
  if (isPromotionExpired(item, now)) {
    const endText = normalizeText(item?.promotionEndTime ?? item?.promotion_end_time)
    return endText ? `推广期已于 ${endText} 结束` : '推广期已结束'
  }
  if (!isPromotingAllianceStatus(item)) {
    const allianceText = normalizeText(item?.statusText) || `状态码 ${item?.status ?? '-'}`
    return `联盟推广状态为「${allianceText}」，需为「推广中」才可展示`
  }
  return null
}

/** 待审核 / 未入库场景下，说明审核通过后能否进入共享商品库列表 */
function resolvePreEntryLibraryReadiness(item: any, now: Date): ProductLibraryReadinessView {
  const blockReason = resolveLibraryEntryBlockReason(item, now)
  if (!blockReason) {
    return {
      code: 'READY_AFTER_ENTRY',
      label: '审核入库后可展示',
      tagType: 'success',
      hint: '审核通过后将自动入库；当前联盟为推广中且推广期有效，入库后会出现在共享商品库列表。',
      canDisplayAfterEntry: true
    }
  }
  return {
    code: 'PENDING_AUDIT',
    label: '审核后不可展示',
    tagType: 'warning',
    hint: `审核通过后将自动入库，但不会在共享商品库列表展示：${blockReason}。请待联盟商品变为「推广中」后重新同步活动商品。`,
    canDisplayAfterEntry: false
  }
}

export function resolveProductLibraryReadiness(item: any, now = new Date()): ProductLibraryReadinessView {
  const display = resolveProductLibraryDisplay(item)
  const bizStatus = normalizeText(item?.bizStatus)

  if (bizStatus === 'REJECTED') {
    return {
      code: 'REJECTED',
      label: '审核拒绝',
      tagType: 'default',
      hint: '审核拒绝的商品无法入库，也不会出现在共享商品库列表。',
      canDisplayAfterEntry: false
    }
  }

  if (display.selectedToLibrary && display.libraryVisible) {
    return {
      code: 'LIST_VISIBLE',
      label: '商品库列表可见',
      tagType: 'success',
      hint: '已在共享商品库列表展示，渠道可直接选品。',
      canDisplayAfterEntry: true
    }
  }

  if (display.hiddenFromList) {
    const blockReason = resolveLibraryEntryBlockReason(item, now)
    const hint = display.visibilityHint
      || (blockReason
        ? `已自动入库，但${blockReason}。`
        : '已自动入库，但当前不满足商品库展示规则。')
    return {
      code: 'STORED_HIDDEN',
      label: '已入库·列表不可见',
      tagType: 'warning',
      hint,
      canDisplayAfterEntry: false
    }
  }

  if (bizStatus === 'PENDING_AUDIT' && !display.selectedToLibrary) {
    return resolvePreEntryLibraryReadiness(item, now)
  }

  const canDisplayAfterEntry = canDisplayInLibraryAfterEntry(item, now)
  if (canDisplayAfterEntry) {
    return {
      code: 'READY_AFTER_ENTRY',
      label: '审核入库后可展示',
      tagType: 'success',
      hint: '联盟状态为推广中且推广期有效；审核通过并入库后，会出现在共享商品库列表。',
      canDisplayAfterEntry: true
    }
  }

  const blockReason = resolveLibraryEntryBlockReason(item, now)
  return {
    code: 'BLOCKED_AFTER_ENTRY',
    label: '审核入库后不可展示',
    tagType: 'warning',
    hint: blockReason
      ? `审核通过并入库后仍不会在共享商品库列表展示：${blockReason}`
      : '审核通过并入库后，当前仍不满足商品库展示规则。',
    canDisplayAfterEntry: false
  }
}

export function resolveProductLibraryDisplay(item: any): ProductLibraryDisplayView {
  const selectedToLibrary = Boolean(item?.selectedToLibrary)
  const displayStatus = normalizeDisplayStatus(item?.displayStatus ?? item?.displayMark)
  const displayStatusLabel = normalizeText(item?.displayMarkLabel || item?.displayStatusLabel)
    || (displayStatus ? DISPLAY_STATUS_LABELS[displayStatus] : '待定')
  const hiddenReason = normalizeText(item?.hiddenReason) || null
  const hiddenReasonLabel = resolveHiddenReasonLabel(hiddenReason)
  const libraryVisible = item?.libraryVisible !== undefined && item?.libraryVisible !== null
    ? Boolean(item.libraryVisible)
    : displayStatus === 'DISPLAYING'
  const hiddenFromList = selectedToLibrary && !libraryVisible
  const allianceStatusText = normalizeText(item?.statusText)

  let entryLabel = '待入库'
  let entryTagType: ProductLibraryDisplayView['entryTagType'] = 'default'
  let listVisibilityLabel = '未入库'
  let visibilityHint: string | null = null

  if (selectedToLibrary && libraryVisible) {
    entryLabel = '已入商品库'
    entryTagType = 'success'
    listVisibilityLabel = '商品库列表可见'
  } else if (selectedToLibrary) {
    entryLabel = '已入库·列表不可见'
    entryTagType = 'warning'
    listVisibilityLabel = '商品库列表不可见'
    if (hiddenReason === 'NOT_ELIGIBLE' && allianceStatusText) {
      visibilityHint = `当前联盟推广状态为「${allianceStatusText}」。共享商品库仅展示联盟状态为「推广中」的商品；活动「推广中」不代表每个商品都可展示。`
    } else if (hiddenReasonLabel) {
      visibilityHint = `${hiddenReasonLabel}，因此不会在共享商品库列表中展示。`
    } else {
      visibilityHint = '商品已完成入库，但当前不满足商品库展示规则，不会在共享商品库列表中展示。'
    }
  } else if (displayStatus === 'PENDING') {
    listVisibilityLabel = '入库后待定'
  }

  return {
    selectedToLibrary,
    libraryVisible,
    displayStatus,
    displayStatusLabel,
    hiddenReason,
    hiddenReasonLabel,
    entryLabel,
    entryTagType,
    listVisibilityLabel,
    visibilityHint,
    hiddenFromList
  }
}

export function mergeLibraryDisplayFields(item: any) {
  const display = resolveProductLibraryDisplay(item)
  const readiness = resolveProductLibraryReadiness(item)
  return {
    ...item,
    selectedToLibrary: display.selectedToLibrary,
    libraryVisible: display.libraryVisible,
    displayStatus: display.displayStatus,
    displayStatusLabel: display.displayStatusLabel,
    hiddenReasonLabel: display.hiddenReasonLabel,
    libraryEntryLabel: display.entryLabel,
    libraryVisibilityHint: display.visibilityHint,
    libraryHiddenFromList: display.hiddenFromList,
    libraryListVisibilityLabel: display.listVisibilityLabel,
    libraryReadinessCode: readiness.code,
    libraryReadinessLabel: readiness.label,
    libraryReadinessHint: readiness.hint,
    libraryCanDisplayAfterEntry: readiness.canDisplayAfterEntry,
    libraryStatusTagLabel: readiness.label,
    libraryStatusTagType: readiness.tagType,
    libraryStatusHint: readiness.hint
  }
}

export function getLibraryDisplayTags(item: any, options: { manageMode?: boolean } = {}) {
  const display = resolveProductLibraryDisplay(item)
  const readiness = resolveProductLibraryReadiness(item)
  const tags: Array<{ text: string; type: 'success' | 'warning' | 'info' | 'default' }> = []

  if (options.manageMode !== false && !display.selectedToLibrary) {
    if (['READY_AFTER_ENTRY', 'BLOCKED_AFTER_ENTRY', 'PENDING_AUDIT', 'REJECTED'].includes(readiness.code)) {
      tags.push({ text: readiness.label, type: readiness.tagType })
    }
    return tags
  }

  if (display.selectedToLibrary && display.libraryVisible) {
    tags.push({ text: '商品库可见', type: 'success' })
  } else if (display.hiddenFromList) {
    tags.push({ text: readiness.label, type: 'warning' })
  } else if (readiness.code === 'BLOCKED_AFTER_ENTRY' || readiness.code === 'PENDING_AUDIT') {
    tags.push({ text: readiness.label, type: readiness.tagType })
  }
  return tags
}

export function formatLibraryEntrySuccessMessage(item: any) {
  const readiness = resolveProductLibraryReadiness(item)
  const display = resolveProductLibraryDisplay(item)
  if (display.libraryVisible) {
    return '审核通过并已入库，当前商品在共享商品库列表可见'
  }
  if (display.hiddenFromList || !readiness.canDisplayAfterEntry) {
    const hint = readiness.hint || display.visibilityHint
    return hint
      ? `审核通过并已自动入库，但暂不在共享商品库列表展示：${hint}`
      : '审核通过并已自动入库，但当前不满足商品库展示条件，暂不在共享商品库列表展示'
  }
  return '审核通过，已加入商品库'
}

export function formatProductSyncTime(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text) return '—'
  const normalized = text.replace('T', ' ').replace(/\.\d+Z?$/, '')
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized
}

export interface ProductCardView {
  id: string
  relationId: string
  productId: string
  productName: string
  imageUrl: string
  shopName: string
  partnerName: string
  activityId: string
  activityName: string
  recruiterName: string
  syncTimeText: string
  colonelName: string
  livePrice: string
  commissionRate: string
  serviceFeeRate: string
  campaignCommissionRate: string
  campaignServiceFeeRate: string
  totalSales: number
  totalSalesText: string
  sampleRequirement: string
  activityStartTime: string
  activityEndTime: string
  isPinned: boolean
  supportInvestment: boolean
  productUrl: string
  baiyingUrl: string
  specs: unknown[]
  raw: Record<string, unknown>
}

const getAuditSupplement = (item: any) => item?.auditSupplement || item?.auditSupplementSummary || {}

export function resolveSupportInvestment(item: any): boolean {
  if (item?.supportInvestment === true || item?.supportsAds === true) return true
  const supplement = getAuditSupplement(item)
  return supplement?.supportsAds === true
}

export function buildSampleRequirementText(item: any): string {
  const supplement = getAuditSupplement(item)
  const parts: string[] = []
  if (supplement?.sampleThresholdSales !== undefined && supplement?.sampleThresholdSales !== null) {
    parts.push(`近30天销售额≥${supplement.sampleThresholdSales}`)
  }
  if (supplement?.sampleThresholdLevel !== undefined && supplement?.sampleThresholdLevel !== null) {
    parts.push(`达人等级≥LV${supplement.sampleThresholdLevel}`)
  }
  const remark = normalizeText(supplement?.sampleThresholdRemark)
  if (remark) parts.push(remark)
  if (parts.length) return parts.join('；')
  if (item?.hasSampleRule === false) return '暂无寄样要求'
  return '未设置寄样门槛'
}

export function formatTotalSalesWan(value: number | null | undefined): string {
  const sales = Number(value ?? 0)
  if (!Number.isFinite(sales) || sales <= 0) return '0'
  if (sales >= 10000) {
    const wan = sales / 10000
    const text = wan >= 10 ? String(Math.round(wan)) : wan.toFixed(1).replace(/\.0$/, '')
    return `${text}W`
  }
  return String(Math.round(sales))
}

const formatRateText = (value: unknown): string => {
  const text = normalizeText(value as string | number | null | undefined)
  if (!text) return '-'
  return text.includes('%') ? text : `${text}%`
}

const formatMoneyText = (value: unknown): string => {
  const text = normalizeText(value as string | number | null | undefined)
  if (!text) return '-'
  if (text.startsWith('¥')) return text
  const num = Number(String(text).replace(/[^\d.]/g, ''))
  if (Number.isFinite(num) && num > 0) return `¥${text.replace(/[^\d.]/g, '')}`
  return text
}

const resolveServiceFeeRateText = (raw: any): string => {
  const labeled = normalizeText(
    raw?.serviceFeeRateText || raw?.dailyServiceFeeRateText || raw?.serviceFeeRate
  )
  if (labeled) return formatRateText(labeled)
  const estimated = normalizeText(raw?.estimatedServiceFee || raw?.estimatedServiceFeeAmount)
  if (estimated) return estimated.startsWith('¥') ? estimated : `¥${estimated}`
  return '-'
}

export function normalizeProductCard(raw: any): ProductCardView {
  const supplement = getAuditSupplement(raw)
  const sales = Number(raw?.totalSales ?? raw?.sales30d ?? raw?.sales ?? 0)
  const activityId = normalizeText(raw?.sourceActivityId || raw?.activityId)
  const productId = normalizeText(raw?.productId)
  const relationId = normalizeText(raw?.id || raw?.relationId)
  const productName = normalizeText(raw?.productName || raw?.title || raw?.name) || '未命名商品'
  const imageUrl = normalizeText(raw?.imageUrl || raw?.cover || raw?.mainImage)
  const shopName = normalizeText(raw?.shopName || raw?.merchantShopName || raw?.merchantName) || '未识别店铺'
  const productUrl =
    normalizeText(raw?.productUrl || raw?.detailUrl || raw?.promotionLink || raw?.promoteLink) || ''
  const baiyingUrl =
    normalizeText(raw?.baiyingUrl || raw?.baiyingLink || raw?.buyinUrl || raw?.detailUrl) || ''

  return {
    id: relationId,
    relationId,
    productId,
    productName,
    imageUrl,
    shopName,
    partnerName: normalizeText(raw?.partnerName || raw?.merchantName || raw?.colonelName),
    activityId,
    activityName: normalizeText(raw?.activityName || raw?.boundActivityName),
    recruiterName: normalizeText(raw?.recruiterName || raw?.assigneeName),
    syncTimeText: formatProductSyncTime(raw?.syncTime ?? raw?.sync_time),
    colonelName: normalizeText(raw?.colonelName || raw?.partnerName),
    livePrice: formatMoneyText(raw?.livePriceText || raw?.priceText || raw?.price),
    commissionRate: formatRateText(raw?.commissionRateText || raw?.activityCosRatioText || raw?.commissionRate),
    serviceFeeRate: resolveServiceFeeRateText(raw),
    campaignCommissionRate: formatRateText(
      raw?.campaignCommissionRateText || raw?.deliveryCommissionRateText || raw?.putCommissionRateText
    ),
    campaignServiceFeeRate: formatRateText(
      raw?.campaignServiceFeeRateText || raw?.putServiceFeeRateText || supplement?.specialCommissionRatio
    ),
    totalSales: Number.isFinite(sales) ? sales : 0,
    totalSalesText: formatTotalSalesWan(sales),
    sampleRequirement: buildSampleRequirementText(raw),
    activityStartTime: normalizeText(
      raw?.activityStartTime || raw?.promotionStartTime || raw?.startTime
    ),
    activityEndTime: normalizeText(raw?.activityEndTime || raw?.promotionEndTime || raw?.endTime),
    isPinned: Boolean(raw?.isPinned ?? raw?.pinned),
    supportInvestment: resolveSupportInvestment(raw),
    productUrl,
    baiyingUrl,
    specs: Array.isArray(raw?.specs)
      ? raw.specs
      : Array.isArray(raw?.specPrices)
        ? raw.specPrices
        : [],
    raw: raw && typeof raw === 'object' ? { ...raw } : {}
  }
}
