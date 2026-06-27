import { describe, expect, it } from 'vitest'

import {
  buildSampleRequirementText,
  canDisplayInLibraryAfterEntry,
  formatLibraryEntrySuccessMessage,
  formatTotalSalesWan,
  getLibraryDisplayTags,
  hasDoubleCommission,
  isPromotionExpired,
  isPromotingAllianceStatus,
  mergeLibraryDisplayFields,
  normalizeDisplayStatus,
  normalizeProductCard,
  parsePromotionEndTime,
  resolveLibraryEntryBlockReason,
  resolveProductLibraryDisplay,
  resolveProductLibraryReadiness,
  resolveSupportInvestment,
  shouldShowLibraryEntryAction
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
      activityStatus: 5,
      bizStatus: 'PENDING_AUDIT',
      status: 2,
      statusText: '申请未通过',
      promotionEndTime: '2026-12-31'
    })
    expect(readiness.code).toBe('PENDING_AUDIT')
    expect(readiness.label).toBe('审核后不可展示')
    expect(readiness.canDisplayAfterEntry).toBe(false)
    expect(readiness.hint).toContain('申请未通过')
    expect(readiness.hint).toContain('商品自身联盟状态')
  })

  it('blocks expired promotion products before entry', () => {
    expect(canDisplayInLibraryAfterEntry({
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2020-01-01'
    })).toBe(false)
  })

  it('detects double commission from upstream cos type before manual tags', () => {
    expect(hasDoubleCommission({ cosType: 1 })).toBe(true)
    expect(hasDoubleCommission({ cos_type: '1' })).toBe(true)
    expect(hasDoubleCommission({ cosType: 0 })).toBe(false)
    expect(hasDoubleCommission({ doubleCommission: true })).toBe(true)
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

  it('allows library entry action based on upstream promoting status', () => {
    expect(shouldShowLibraryEntryAction({
      selectedToLibrary: false,
      status: 1,
      bizStatus: 'PENDING_AUDIT',
      auditStatus: 3
    }, true)).toBe(true)

    expect(shouldShowLibraryEntryAction({
      selectedToLibrary: false,
      status: 0,
      statusText: '待审核',
      bizStatus: 'APPROVED'
    }, true)).toBe(false)
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
      productUrl: 'https://item.jd.com/origin.html',
      baiyingUrl: 'https://buyin.example/manage',
      promotionLink: 'https://v.douyin.com/abc/?pick_source=ps_001',
      promotionStartTime: '2026-01-01',
      promotionEndTime: '2026-12-31',
      syncTime: '2026-05-29T16:52:00',
      auditSupplement: { sampleThresholdSales: 1000, supportsAds: true }
    })
    expect(card.productName).toBe('测试商品')
    expect(card.imageUrl).toContain('img.example')
    expect(card.isPinned).toBe(true)
    expect(card.supportInvestment).toBe(true)
    expect(card.totalSalesText).toBe('2.5W')
    expect(card.recruiterName).toBe('张三')
    expect(card.syncTimeText).toBe('2026-05-29 16:52')
    expect(card.shopName).toBe('旗舰店')
    expect(card.livePrice).toBe('¥99')
    expect(card.commissionRate).toBe('20%')
    // 三种链接字段严格分离（ADR-003）：
    // - productUrl 不再被 promotionLink 兜底污染
    // - baiyingUrl 不再被 productUrl / detailUrl 兜底污染
    // - promotionUrl 独立承载真实转链
    expect(card.productUrl).toBe('https://item.jd.com/origin.html')
    expect(card.baiyingUrl).toBe('https://buyin.example/manage')
    expect(card.promotionUrl).toBe('https://v.douyin.com/abc/?pick_source=ps_001')
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
    expect(message).toBe('审核通过并已入库，当前商品在共享商品库列表可见')
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
    expect(message).toBe('审核通过，已加入商品库')
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

  it('getLibraryDisplayTags returns audit-blocked tag before entry', () => {
    const tags = getLibraryDisplayTags({
      bizStatus: 'PENDING_AUDIT',
      status: 0,
      statusText: '待审核',
      promotionEndTime: '2027-05-31'
    }, { manageMode: true })
    expect(tags).toEqual([{ text: '审核后不可展示', type: 'warning' }])
  })

  it('mergeLibraryDisplayFields exposes libraryStatusHint for list rendering', () => {
    const merged = mergeLibraryDisplayFields({
      bizStatus: 'PENDING_AUDIT',
      activityStatus: 5,
      status: 0,
      statusText: '待审核',
      promotionEndTime: '2027-05-31'
    })
    expect(merged.libraryStatusHint).toContain('商品自身联盟状态')
    expect(merged.libraryStatusTagLabel).toBe('审核后不可展示')
  })

  describe('normalizeProductCard - 商品库 hover 抽屉字段', () => {
    it('透传 merchantName / productStock / shopScore', () => {
      const view = normalizeProductCard({
        productId: 'P-1001',
        title: '测试商品',
        merchantName: '品牌方A',
        productStock: '50',
        shopScore: 90
      })
      expect(view.merchantName).toBe('品牌方A')
      expect(view.productStock).toBe('50')
      expect(view.shopScore).toBe(90)
    })

    it('shopScore 支持 String 输入（兼容抖音 gateway）', () => {
      const view = normalizeProductCard({
        productId: 'P-1002',
        shopScore: '85'
      })
      expect(view.shopScore).toBe(85)
    })

    it('shopScore 缺失时为 null，不抛错', () => {
      const view = normalizeProductCard({
        productId: 'P-1003'
      })
      expect(view.shopScore).toBeNull()
    })

    it('shopScore 非法字符串时为 null', () => {
      const view = normalizeProductCard({
        productId: 'P-1004',
        shopScore: 'N/A'
      })
      expect(view.shopScore).toBeNull()
    })

    it('productStock 缺失时为空字符串', () => {
      const view = normalizeProductCard({
        productId: 'P-1005'
      })
      expect(view.productStock).toBe('')
    })

    it('merchantName 兜底到 partnerName', () => {
      const view = normalizeProductCard({
        productId: 'P-1006',
        partnerName: '团长兼商家A'
      })
      expect(view.merchantName).toBe('团长兼商家A')
    })
  })

  /**
   * ADR-003：三种链接字段必须严格分离兜底链。
   * - productUrl：上游原始商品链接（不含 pick_source）
   * - baiyingUrl：百应后台商品链接
   * - promotionUrl：真实转链后的推广链接（带 pick_source）
   */
  describe('normalizeProductCard - 三种链接字段兜底分离 (ADR-003)', () => {
    it('仅有 promotionLink 时，productUrl 必须为空（不允许把转链当商品原链展示）', () => {
      const view = normalizeProductCard({
        productId: 'P-2001',
        promotionLink: 'https://v.douyin.com/abc/?pick_source=ps_001'
      })
      expect(view.productUrl).toBe('')
      expect(view.promotionUrl).toBe('https://v.douyin.com/abc/?pick_source=ps_001')
    })

    it('仅有 promoteLink / shortLink / promotionUrl 时，promotionUrl 取第一个非空值', () => {
      const a = normalizeProductCard({ productId: 'P-2002', promoteLink: 'https://a.douyin.com/p' })
      expect(a.promotionUrl).toBe('https://a.douyin.com/p')

      const b = normalizeProductCard({ productId: 'P-2003', shortLink: 'https://b.douyin.com/s' })
      expect(b.promotionUrl).toBe('https://b.douyin.com/s')

      const c = normalizeProductCard({ productId: 'P-2004', promotionUrl: 'https://c.douyin.com/u' })
      expect(c.promotionUrl).toBe('https://c.douyin.com/u')
    })

    it('仅有 productUrl 时，baiyingUrl 必须为空（不允许把商品原链当百应链接）', () => {
      const view = normalizeProductCard({
        productId: 'P-2005',
        productUrl: 'https://item.jd.com/123.html'
      })
      expect(view.baiyingUrl).toBe('')
      expect(view.productUrl).toBe('https://item.jd.com/123.html')
    })

    it('仅有 baiyingUrl / baiyingLink / buyinUrl 时，baiyingUrl 取第一个非空值', () => {
      expect(
        normalizeProductCard({ productId: 'P-2006', baiyingUrl: 'https://buyin.example/a' }).baiyingUrl
      ).toBe('https://buyin.example/a')
      expect(
        normalizeProductCard({ productId: 'P-2007', baiyingLink: 'https://buyin.example/b' }).baiyingUrl
      ).toBe('https://buyin.example/b')
      expect(
        normalizeProductCard({ productId: 'P-2008', buyinUrl: 'https://buyin.example/c' }).baiyingUrl
      ).toBe('https://buyin.example/c')
    })

    it('三种字段同时存在时互不污染', () => {
      const view = normalizeProductCard({
        productId: 'P-2009',
        productUrl: 'https://item.jd.com/origin.html',
        baiyingUrl: 'https://buyin.example/manage',
        promotionLink: 'https://v.douyin.com/abc/?pick_source=ps_001'
      })
      expect(view.productUrl).toBe('https://item.jd.com/origin.html')
      expect(view.baiyingUrl).toBe('https://buyin.example/manage')
      expect(view.promotionUrl).toBe('https://v.douyin.com/abc/?pick_source=ps_001')
    })
  })
})
