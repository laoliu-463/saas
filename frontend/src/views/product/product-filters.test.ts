import { describe, expect, it } from 'vitest'

import {
  buildProductLibraryQueryParams,
  categoryNameOptions,
  DEFAULT_PRODUCT_FILTERS,
  libraryCategoryOptionsPreset,
  matchAuditTags,
  matchPromotionLink
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
})
