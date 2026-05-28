import { describe, expect, it } from 'vitest'

import {
  applyProductFilters,
  buildActivityProductInfoQuery,
  buildProductLibraryQueryParams,
  categoryNameOptions,
  DEFAULT_PRODUCT_FILTERS,
  formatGmv30d,
  formatSales30d,
  libraryCategoryOptionsPreset,
  matchAllianceStatus,
  matchAssignee,
  matchAuditTags,
  matchCategoryName,
  matchCommission,
  matchDecision,
  matchHasSample,
  matchPromotionLink,
  matchSalesRange,
  matchShopKeyword,
  matchSystemTag,
  productDomainCategoryOptions,
  systemTagOptions,
  yesNoOptions
} from './product-filters'

describe('product filters', () => {
  it('libraryCategoryOptionsPreset is empty and not used as primary category source', () => {
    expect(libraryCategoryOptionsPreset).toEqual([])
  })

  it('keeps all product-domain category presets available for activity filters', () => {
    expect(categoryNameOptions.map((option) => option.label)).toEqual([
      '玩具乐器',
      '服饰内衣',
      '个护家清',
      '智能家居',
      '生鲜',
      '美妆',
      '母婴宠物',
      '鲜花园艺',
      '本地生活',
      '食品饮料',
      '3C数码家电',
      '图书教育',
      '鞋靴箱包',
      '虚拟充值',
      '运动户外',
      '钟表配饰',
      '珠宝文玩',
      '医疗健康',
      '酒类',
      '滋补保健',
      '原料包装',
      '餐饮外卖'
    ])
  })

  it('buildProductLibraryQueryParams maps P1 toolbar filters to backend query keys', () => {
    const filters = {
      ...DEFAULT_PRODUCT_FILTERS(),
      shopKeyword: '清风',
      categories: ['食品饮料', '美妆护肤'],
      activityId: '10001',
      assigneeId: 'user-1',
      colonelName: '张团长',
      published: '1',
      listed: '1',
      freeSample: '1',
      commissionMin: '10',
      commissionMax: '30',
      recruitActivityId: 'ACT001',
      recruitActivityName: '春季招商',
      materialDownload: true,
      handCard: true,
      doubleCommission: true,
      notInLibrary: true,
      dedup: true
    }

    expect(buildProductLibraryQueryParams(filters, { page: 2, size: 20, keyword: '9001' })).toEqual({
      page: 2,
      size: 20,
      keyword: '9001',
      shopKeyword: '清风',
      categories: '食品饮料,美妆护肤',
      activityId: '10001',
      assigneeId: 'user-1',
      colonelName: '张团长',
      published: '1',
      listed: '1',
      freeSample: '1',
      commissionMin: '10',
      commissionMax: '30',
      recruitActivityId: 'ACT001',
      recruitActivityName: '春季招商',
      materialDownload: '1',
      handCard: '1',
      doubleCommission: '1',
      notInLibrary: '1',
      dedup: '1'
    })
  })

  it('buildProductLibraryQueryParams omits unchecked boolean filters', () => {
    const params = buildProductLibraryQueryParams(DEFAULT_PRODUCT_FILTERS(), { page: 1, size: 12 })
    expect(params.materialDownload).toBeUndefined()
    expect(params.listed).toBeUndefined()
    expect(params.freeSample).toBeUndefined()
  })

  it('buildProductLibraryQueryParams forwards partner scope and sort', () => {
    expect(buildProductLibraryQueryParams(DEFAULT_PRODUCT_FILTERS(), {
      partnerId: '7351155267604218149',
      partnerType: 'COLONEL',
      sortBy: 'latest'
    })).toMatchObject({
      partnerId: '7351155267604218149',
      partnerType: 'COLONEL',
      sortBy: 'latest'
    })
  })

  it('treats ready or existing promotion links as linked', () => {
    expect(matchPromotionLink({ promotionLinkStatus: 'READY', promotionLink: 'https://v.douyin.com/ready/' }, 'LINKED')).toBe(true)
    expect(matchPromotionLink({ shortLink: 'https://v.douyin.com/short/' }, 'LINKED')).toBe(true)
    expect(matchPromotionLink({ promotion: { link: 'https://v.douyin.com/promo/' } }, 'LINKED')).toBe(true)
  })

  it('keeps explicit failed and pending promotion states distinct', () => {
    expect(matchPromotionLink({ promotionLinkStatus: 'FAILED' }, 'FAILED')).toBe(true)
    expect(matchPromotionLink({ promotionLinkStatus: 'FAILED' }, 'PENDING')).toBe(false)
    expect(matchPromotionLink({}, 'PENDING')).toBe(true)
  })

  it('matches audit goods and product tags from supplement payloads', () => {
    const item = {
      auditSupplement: {
        goodsTags: ['家居', '零食'],
        productTags: ['主推']
      }
    }

    expect(matchAuditTags(item, ['家居'], ['主推'])).toBe(true)
    expect(matchAuditTags(item, ['美妆'], ['主推'])).toBe(false)
    expect(matchAuditTags({ auditSupplementSummary: { goods_tags: ['美妆'], product_tags: ['次推'] } }, ['美妆'], ['次推'])).toBe(true)
  })

  it('matchSystemTag returns true when no filter is active', () => {
    expect(matchSystemTag({}, null)).toBe(true)
  })

  it('matchSystemTag matches high_commission tag', () => {
    expect(matchSystemTag({ systemTags: ['高佣'] }, 'high_commission')).toBe(true)
    expect(matchSystemTag({ systemTags: ['普通'] }, 'high_commission')).toBe(false)
  })

  it('matchSystemTag matches traffic tag (抖音商品池)', () => {
    expect(matchSystemTag({ systemTags: ['抖音商品池'] }, 'traffic')).toBe(true)
  })

  it('matchSystemTag matches new tag (low sales)', () => {
    expect(matchSystemTag({ sales30d: 50 }, 'new')).toBe(true)
    expect(matchSystemTag({ sales30d: 200 }, 'new')).toBe(false)
  })

  it('matchSystemTag matches high_price tag (price >= 300)', () => {
    expect(matchSystemTag({ priceText: '¥399' }, 'high_price')).toBe(true)
    expect(matchSystemTag({ priceText: '¥199' }, 'high_price')).toBe(false)
  })

  it('matchCommission handles all commission ranges', () => {
    expect(matchCommission({ activityCosRatioText: '25%' }, 'gt20')).toBe(true)
    expect(matchCommission({ activityCosRatioText: '15%' }, 'gt20')).toBe(false)
    expect(matchCommission({ activityCosRatioText: '15%' }, '10_20')).toBe(true)
    expect(matchCommission({ activityCosRatioText: '8%' }, 'lt10')).toBe(true)
  })

  it('matchHasSample handles hasSample filter', () => {
    expect(matchHasSample({ hasSampleRule: true }, '1')).toBe(true)
    expect(matchHasSample({ hasSampleRule: false }, '1')).toBe(false)
    expect(matchHasSample({ hasSampleRule: false }, '0')).toBe(true)
    expect(matchHasSample({ hasSampleRule: true }, '0')).toBe(false)
  })

  it('matchAssignee handles assignee filter', () => {
    expect(matchAssignee({ assigneeName: '张三' }, 'assigned')).toBe(true)
    expect(matchAssignee({ assigneeName: null }, 'assigned')).toBe(false)
    expect(matchAssignee({ assigneeName: null }, 'unassigned')).toBe(true)
  })

  it('matchDecision handles decision filter', () => {
    expect(matchDecision({ latestDecisionLevel: 'MAIN' }, 'MAIN')).toBe(true)
    expect(matchDecision({ latestDecisionLevel: 'SECONDARY' }, 'MAIN')).toBe(false)
    expect(matchDecision({}, 'NONE')).toBe(true)
    expect(matchDecision({ latestDecisionLevel: 'PAUSE' }, 'NONE')).toBe(false)
  })

  it('matchShopKeyword performs case-insensitive substring match', () => {
    expect(matchShopKeyword({ shopName: '清风旗舰店' }, '清风')).toBe(true)
    expect(matchShopKeyword({ shopName: 'ABC商店' }, 'abc')).toBe(true)
    expect(matchShopKeyword({ shopName: '其他店' }, '清风')).toBe(false)
  })

  it('matchCategoryName performs case-insensitive category match', () => {
    expect(matchCategoryName({ categoryName: '食品饮料' }, '食品')).toBe(true)
    expect(matchCategoryName({ categoryName: '美妆' }, '美妆')).toBe(true)
    expect(matchCategoryName({ categoryName: '美妆' }, 'MZ')).toBe(false)
  })

  it('matchSalesRange handles all sales ranges', () => {
    expect(matchSalesRange({ sales30d: 50 }, 'lt100')).toBe(true)
    expect(matchSalesRange({ sales30d: 500 }, '100_999')).toBe(true)
    expect(matchSalesRange({ sales30d: 5000 }, '1k_29k')).toBe(true)
    expect(matchSalesRange({ sales30d: 30000 }, 'gte30000')).toBe(true)
  })

  it('matchAllianceStatus matches promoting status', () => {
    expect(matchAllianceStatus({ statusText: '推广中' }, 'promoting')).toBe(true)
    expect(matchAllianceStatus({ statusText: '推广' }, 'promoting')).toBe(true)
  })

  it('matchAllianceStatus matches rejected status', () => {
    expect(matchAllianceStatus({ statusText: '申请未通过' }, 'rejected')).toBe(true)
    expect(matchAllianceStatus({ statusText: '未通过' }, 'rejected')).toBe(true)
  })

  it('matchAllianceStatus matches expired status', () => {
    expect(matchAllianceStatus({ statusText: '合作已过期' }, 'expired')).toBe(true)
  })

  it('applyProductFilters filters items correctly', () => {
    const items = [
      { bizStatus: 'PENDING_AUDIT', systemTags: ['高佣'], sales30d: 500, shopName: '测试店' },
      { bizStatus: 'APPROVED', systemTags: [], sales30d: 50, shopName: '其他店' }
    ]
    const filters = { ...DEFAULT_PRODUCT_FILTERS(), systemTag: 'high_commission', shopKeyword: '测试' }
    const result = applyProductFilters(items, filters, null)
    expect(result.length).toBe(1)
    expect(result[0].shopName).toBe('测试店')
  })

  it('buildActivityProductInfoQuery prioritizes productId over keyword', () => {
    expect(buildActivityProductInfoQuery('P001', 'search term', 'shop')).toBe('P001')
  })

  it('buildActivityProductInfoQuery falls back to keyword then shop', () => {
    expect(buildActivityProductInfoQuery(null, 'search term', null)).toBe('search term')
    expect(buildActivityProductInfoQuery(null, '', 'shop')).toBe('shop')
    expect(buildActivityProductInfoQuery(null, '', null)).toBeUndefined()
  })

  it('formatSales30d formats sales numbers', () => {
    expect(formatSales30d({ sales30d: 15000 })).toBe('1.5万')
    expect(formatSales30d({ sales: 500 })).toBe('500')
    expect(formatSales30d({})).toBe('0')
  })

  it('formatGmv30d formats GMV correctly', () => {
    expect(formatGmv30d({ gmv30d: 1500000 })).toBe('¥150.00万')
    expect(formatGmv30d({ gmv30d: 500 })).toBe('¥500.00')
    expect(formatGmv30d({ gmv30d: null })).toBe('-')
    expect(formatGmv30d({ gmv30d: 0 })).toBe('0')
  })

  it('productDomainCategoryOptions contains 22 categories', () => {
    expect(productDomainCategoryOptions.length).toBe(22)
    expect(productDomainCategoryOptions[0].label).toBe('玩具乐器')
  })

  it('systemTagOptions and yesNoOptions are defined', () => {
    expect(systemTagOptions.length).toBeGreaterThan(0)
    expect(yesNoOptions.length).toBe(2)
  })
})
