import { describe, expect, it } from 'vitest'

import {
  buildSampleRequirementText,
  canDisplayInLibraryAfterEntry,
  formatLibraryEntrySuccessMessage,
  formatTotalSalesWan,
  getLibraryDisplayTags,
  isPromotionExpired,
  isPromotingAllianceStatus,
  mergeLibraryDisplayFields,
  normalizeDisplayStatus,
  normalizeProductCard,
  parsePromotionEndTime,
  resolveLibraryEntryBlockReason,
  resolveProductLibraryDisplay,
  resolveProductLibraryReadiness,
  resolveSupportInvestment
} from './product-library-display'

describe('product-library-display', () => {
  it('treats selected + displaying as library visible', () => {
    const view = resolveProductLibraryDisplay({
      selectedToLibrary: true,
      libraryVisible: true,
      displayStatus: 'DISPLAYING'
    })
    expect(view.entryLabel).toBe('已入商品库')
    expect(view.hiddenFromList).toBe(false)
  })

  it('does not infer library visibility from bizStatus alone', () => {
    const view = resolveProductLibraryDisplay({
      bizStatus: 'APPROVED',
      selectedToLibrary: false,
      libraryVisible: false
    })
    expect(view.selectedToLibrary).toBe(false)
    expect(view.libraryVisible).toBe(false)
    expect(view.hiddenFromList).toBe(false)
  })

  it('explains hidden library entry with alliance status context', () => {
    const view = resolveProductLibraryDisplay({
      selectedToLibrary: true,
      libraryVisible: false,
      displayStatus: 'HIDDEN',
      hiddenReason: 'NOT_ELIGIBLE',
      statusText: '申请未通过'
    })
    expect(view.entryLabel).toBe('已入库·列表不可见')
    expect(view.hiddenFromList).toBe(true)
    expect(view.visibilityHint).toContain('申请未通过')
    expect(view.visibilityHint).toContain('推广中')
  })

  it('marks promoting alliance products as ready after entry', () => {
    const readiness = resolveProductLibraryReadiness({
      bizStatus: 'PENDING_AUDIT',
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2026-12-31'
    })
    expect(readiness.code).toBe('READY_AFTER_ENTRY')
    expect(readiness.canDisplayAfterEntry).toBe(true)
    expect(readiness.label).toBe('审核入库后可展示')
  })

  it('blocks non-promoting alliance products before entry', () => {
    const readiness = resolveProductLibraryReadiness({
      bizStatus: 'PENDING_AUDIT',
      status: 2,
      statusText: '申请未通过',
      promotionEndTime: '2026-12-31'
    })
    expect(readiness.code).toBe('BLOCKED_AFTER_ENTRY')
    expect(readiness.canDisplayAfterEntry).toBe(false)
    expect(readiness.hint).toContain('申请未通过')
  })

  it('blocks expired promotion products before entry', () => {
    expect(canDisplayInLibraryAfterEntry({
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2020-01-01'
    })).toBe(false)
  })

  it('mergeLibraryDisplayFields stops inflating libraryVisible from approved status', () => {
    const merged = mergeLibraryDisplayFields({
      bizStatus: 'APPROVED',
      selectedToLibrary: true,
      libraryVisible: false,
      displayStatus: 'HIDDEN',
      hiddenReason: 'NOT_ELIGIBLE',
      statusText: '申请未通过'
    })
    expect(merged.libraryVisible).toBe(false)
    expect(merged.libraryHiddenFromList).toBe(true)
    expect(merged.libraryCanDisplayAfterEntry).toBe(false)
  })

  it('builds manage-mode tag for display-ready products', () => {
    const tags = getLibraryDisplayTags({
      bizStatus: 'PENDING_AUDIT',
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2026-12-31'
    }, { manageMode: true })
    expect(tags).toEqual([{ text: '审核入库后可展示', type: 'success' }])
  })

  it('builds warning tag when product is stored but hidden from shared library', () => {
    const tags = getLibraryDisplayTags({
      selectedToLibrary: true,
      libraryVisible: false,
      displayStatus: 'HIDDEN'
    })
    expect(tags).toEqual([{ text: '已入库·列表不可见', type: 'warning' }])
  })

  it('formats success message for hidden library products', () => {
    const message = formatLibraryEntrySuccessMessage({
      selectedToLibrary: true,
      libraryVisible: false,
      hiddenReason: 'NOT_ELIGIBLE',
      statusText: '申请未通过'
    })
    expect(message).toContain('暂不在共享商品库列表展示')
    expect(message).toContain('申请未通过')
  })

  it('normalizeProductCard maps API fields with safe fallbacks', () => {
    const card = normalizeProductCard({
      id: 'rel-1',
      productId: '9001',
      title: '测试商品',
      cover: 'https://img.example/p.jpg',
      shopName: '旗舰店',
      assigneeName: '张三',
      activityId: 'act-1',
      activityName: '春季活动',
      priceText: '¥99',
      activityCosRatioText: '20%',
      sales30d: 25000,
      pinned: true,
      supportsAds: true,
      detailUrl: 'https://buyin.example/p',
      promotionStartTime: '2026-01-01',
      promotionEndTime: '2026-12-31',
      auditSupplement: { sampleThresholdSales: 1000, supportsAds: true }
    })
    expect(card.productName).toBe('测试商品')
    expect(card.imageUrl).toContain('img.example')
    expect(card.isPinned).toBe(true)
    expect(card.supportInvestment).toBe(true)
    expect(card.totalSalesText).toBe('2.5W')
    expect(card.recruiterName).toBe('张三')
    expect(card.shopName).toBe('旗舰店')
    expect(card.livePrice).toBe('¥99')
    expect(card.commissionRate).toBe('20%')
    expect(card.productUrl).toBe('https://buyin.example/p')
    expect(card.baiyingUrl).toContain('buyin.example')
    expect(card.activityStartTime).toBe('2026-01-01')
    expect(card.activityEndTime).toBe('2026-12-31')
    expect(card.sampleRequirement).toContain('1000')
  })

  it('normalizeProductCard does not throw on empty payload', () => {
    const card = normalizeProductCard({})
    expect(card.productName).toBe('未命名商品')
    expect(card.livePrice).toBe('-')
    expect(card.totalSales).toBe(0)
    expect(card.raw).toEqual({})
  })

  it('normalizeDisplayStatus maps SHOWING to DISPLAYING', () => {
    expect(normalizeDisplayStatus('SHOWING')).toBe('DISPLAYING')
    expect(normalizeDisplayStatus('showing')).toBe('DISPLAYING')
  })

  it('normalizeDisplayStatus returns null for unknown values', () => {
    expect(normalizeDisplayStatus('UNKNOWN')).toBeNull()
    expect(normalizeDisplayStatus(null)).toBeNull()
    expect(normalizeDisplayStatus(undefined)).toBeNull()
  })

  it('parsePromotionEndTime parses ISO format directly', () => {
    const date = parsePromotionEndTime('2026-06-15T23:59:59')
    expect(date).not.toBeNull()
    expect(date?.getDate()).toBe(15)
  })

  it('parsePromotionEndTime returns null for invalid text', () => {
    expect(parsePromotionEndTime('invalid-date')).toBeNull()
    expect(parsePromotionEndTime('')).toBeNull()
    expect(parsePromotionEndTime(null)).toBeNull()
  })

  it('isPromotionExpired returns true for expired promotion', () => {
    const now = new Date('2026-06-01')
    expect(isPromotionExpired({ promotionEndTime: '2026-01-01' }, now)).toBe(true)
  })

  it('isPromotionExpired returns true when activityExpired flag is set', () => {
    expect(isPromotionExpired({ activityExpired: true })).toBe(true)
  })

  it('isPromotionExpired returns false for valid future date', () => {
    const now = new Date('2026-01-01')
    expect(isPromotionExpired({ promotionEndTime: '2026-12-31' }, now)).toBe(false)
  })

  it('isPromotingAllianceStatus checks status code 1', () => {
    expect(isPromotingAllianceStatus({ status: 1 })).toBe(true)
    expect(isPromotingAllianceStatus({ status: 2 })).toBe(false)
  })

  it('isPromotingAllianceStatus checks statusText keywords', () => {
    expect(isPromotingAllianceStatus({ statusText: '推广中' })).toBe(true)
    expect(isPromotingAllianceStatus({ statusText: '申请未通过' })).toBe(false)
  })

  it('canDisplayInLibraryAfterEntry returns false for manualDisabled', () => {
    expect(canDisplayInLibraryAfterEntry({ manualDisabled: true })).toBe(false)
  })

  it('resolveLibraryEntryBlockReason returns correct message for expired', () => {
    const reason = resolveLibraryEntryBlockReason({ promotionEndTime: '2026-01-01' })
    expect(reason).toContain('推广期')
    expect(reason).toContain('2026-01-01')
  })

  it('resolveLibraryEntryBlockReason returns correct message for non-promoting', () => {
    const reason = resolveLibraryEntryBlockReason({ status: 2, statusText: '申请未通过' })
    expect(reason).toContain('申请未通过')
  })

  it('resolveLibraryEntryBlockReason returns null when product is eligible', () => {
    const reason = resolveLibraryEntryBlockReason({ status: 1, statusText: '推广中', promotionEndTime: '2026-12-31' })
    expect(reason).toBeNull()
  })

  it('resolveProductLibraryReadiness returns REJECTED for rejected products', () => {
    const readiness = resolveProductLibraryReadiness({ bizStatus: 'REJECTED' })
    expect(readiness.code).toBe('REJECTED')
    expect(readiness.canDisplayAfterEntry).toBe(false)
  })

  it('resolveProductLibraryReadiness returns LIST_VISIBLE for visible products', () => {
    const readiness = resolveProductLibraryReadiness({
      selectedToLibrary: true,
      libraryVisible: true
    })
    expect(readiness.code).toBe('LIST_VISIBLE')
    expect(readiness.canDisplayAfterEntry).toBe(true)
  })

  it('resolveProductLibraryReadiness returns STORED_HIDDEN for stored but hidden products', () => {
    const readiness = resolveProductLibraryReadiness({
      selectedToLibrary: true,
      libraryVisible: false,
      displayStatus: 'HIDDEN'
    })
    expect(readiness.code).toBe('STORED_HIDDEN')
    expect(readiness.canDisplayAfterEntry).toBe(false)
  })

  it('formatLibraryEntrySuccessMessage returns full success for visible products', () => {
    const message = formatLibraryEntrySuccessMessage({
      selectedToLibrary: true,
      libraryVisible: true
    })
    expect(message).toBe('已加入商品库，当前商品在共享商品库列表可见')
  })

  it('formatLibraryEntrySuccessMessage returns partial message for hidden products', () => {
    const message = formatLibraryEntrySuccessMessage({
      selectedToLibrary: true,
      libraryVisible: false,
      hiddenReason: 'NOT_ELIGIBLE',
      statusText: '申请未通过'
    })
    expect(message).toContain('暂不在共享商品库列表展示')
  })

  it('formatLibraryEntrySuccessMessage returns basic message for eligible products', () => {
    const message = formatLibraryEntrySuccessMessage({
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2026-12-31'
    })
    expect(message).toBe('已加入商品库')
  })

  it('resolveSupportInvestment checks various source fields', () => {
    expect(resolveSupportInvestment({ supportInvestment: true })).toBe(true)
    expect(resolveSupportInvestment({ supportsAds: true })).toBe(true)
    expect(resolveSupportInvestment({ auditSupplement: { supportsAds: true } })).toBe(true)
    expect(resolveSupportInvestment({})).toBe(false)
  })

  it('buildSampleRequirementText builds text from auditSupplement', () => {
    const text = buildSampleRequirementText({
      auditSupplement: {
        sampleThresholdSales: 1000,
        sampleThresholdLevel: 3
      }
    })
    expect(text).toContain('近30天销售额≥1000')
    expect(text).toContain('达人等级≥LV3')
  })

  it('buildSampleRequirementText handles hasSampleRule false', () => {
    const text = buildSampleRequirementText({ hasSampleRule: false })
    expect(text).toBe('暂无寄样要求')
  })

  it('buildSampleRequirementText returns default for no requirements', () => {
    const text = buildSampleRequirementText({})
    expect(text).toBe('未设置寄样门槛')
  })

  it('formatTotalSalesWan formats numbers correctly', () => {
    expect(formatTotalSalesWan(0)).toBe('0')
    expect(formatTotalSalesWan(9999)).toBe('9999')
    expect(formatTotalSalesWan(10000)).toBe('1W')
    expect(formatTotalSalesWan(15000)).toBe('1.5W')
    expect(formatTotalSalesWan(100000)).toBe('10W')
    expect(formatTotalSalesWan(1000000)).toBe('100W')
    expect(formatTotalSalesWan(null)).toBe('0')
    expect(formatTotalSalesWan(undefined)).toBe('0')
  })

  it('getLibraryDisplayTags returns no tags for selected products in manage mode', () => {
    const tags = getLibraryDisplayTags({
      selectedToLibrary: true,
      libraryVisible: true
    }, { manageMode: true })
    expect(tags).toEqual([{ text: '商品库可见', type: 'success' }])
  })

  it('getLibraryDisplayTags returns REJECTED tag', () => {
    const tags = getLibraryDisplayTags({
      bizStatus: 'REJECTED'
    })
    expect(tags).toEqual([{ text: '审核拒绝', type: 'default' }])
  })
})
