import { describe, expect, it } from 'vitest'

import {
  canDisplayInLibraryAfterEntry,
  formatLibraryEntrySuccessMessage,
  getLibraryDisplayTags,
  mergeLibraryDisplayFields,
  normalizeProductCard,
  resolveProductLibraryDisplay,
  resolveProductLibraryReadiness
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
    expect(card.livePrice).toBe('¥99')
    expect(card.commissionRate).toBe('20%')
    expect(card.baiyingUrl).toContain('buyin.example')
    expect(card.sampleRequirement).toContain('1000')
  })

  it('normalizeProductCard does not throw on empty payload', () => {
    const card = normalizeProductCard({})
    expect(card.productName).toBe('未命名商品')
    expect(card.livePrice).toBe('-')
    expect(card.totalSales).toBe(0)
    expect(card.raw).toEqual({})
  })
})
