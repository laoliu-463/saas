import { describe, expect, it, vi } from 'vitest'

import { buildProductBriefCopy, copyProductBriefWithLink, extractPromotionLink } from './product-copy'

describe('product copy helpers', () => {
  it('builds a product brief with audit supplement, material pack and short link', () => {
    const text = buildProductBriefCopy(
      {
        title: '爆款水杯',
        shopName: '清风小店',
        priceText: '¥39.90',
        activityCosRatioText: '20%',
        sales30d: 1234,
        auditSupplement: {
          sellingPoints: ['大容量', '防漏'],
          promotionScript: '主打夏季补水场景',
          sampleThresholdSales: 5000,
          sampleThresholdLevel: 3,
          exclusivePriceRemark: '达人专属券后价'
        },
        promotionMaterialPack: {
          outreachScript: '兜底话术'
        }
      },
      'https://v.douyin.com/abc/'
    )

    expect(text).toContain('【商品】爆款水杯（清风小店）')
    expect(text).toContain('【售价】¥39.90  【佣金率】20%  【近30天】1234')
    expect(text).toContain('【卖点】大容量、防漏')
    expect(text).toContain('【话术】主打夏季补水场景')
    expect(text).toContain('【寄样门槛】销售额≥5000 / 等级≥LV3')
    expect(text).toContain('【专属价说明】达人专属券后价')
    expect(text).toContain('【链接】https://v.douyin.com/abc/')
  })

  it('omits the link line when promotion link generation fails', () => {
    const text = buildProductBriefCopy({
      title: '半空素材商品',
      shopName: '',
      priceText: '',
      activityCosRatioText: '',
      sales30d: null,
      auditSupplement: {},
      promotionMaterialPack: {
        sellingPoints: ['耐用'],
        outreachScript: '可先发达人测款'
      }
    })

    expect(text).toContain('【商品】半空素材商品（-）')
    expect(text).toContain('【卖点】耐用')
    expect(text).toContain('【话术】可先发达人测款')
    expect(text).not.toContain('【链接】')
  })

  it('reads promotion links from existing product or convert response shapes', () => {
    expect(extractPromotionLink({ promotion: { link: 'https://v.douyin.com/ready/' } })).toBe('https://v.douyin.com/ready/')
    expect(extractPromotionLink({ promoteLink: 'https://v.douyin.com/promote/' })).toBe('https://v.douyin.com/promote/')
    expect(extractPromotionLink({ shortLink: 'https://v.douyin.com/short/' })).toBe('https://v.douyin.com/short/')
  })

  it('converts before copying when the product has no promotion link', async () => {
    const convertLink = vi.fn().mockResolvedValue({ data: { shortLink: 'https://v.douyin.com/generated/' } })
    const writeText = vi.fn().mockResolvedValue(undefined)

    const result = await copyProductBriefWithLink({
      item: { title: '待转链商品', shopName: '测试店铺', auditSupplement: {} },
      activityId: 'A1',
      productId: 'P1',
      scene: 'PRODUCT_LIBRARY',
      convertLink,
      writeText
    })

    expect(convertLink).toHaveBeenCalledWith('A1', 'P1', { scene: 'PRODUCT_LIBRARY' })
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('【链接】https://v.douyin.com/generated/'))
    expect(result.link).toBe('https://v.douyin.com/generated/')
    expect(result.linkGenerationFailed).toBe(false)
  })

  it('copies the brief without link when conversion fails', async () => {
    const convertLink = vi.fn().mockRejectedValue(new Error('convert failed'))
    const writeText = vi.fn().mockResolvedValue(undefined)

    const result = await copyProductBriefWithLink({
      item: { title: '转链失败商品', shopName: '测试店铺', auditSupplement: {} },
      activityId: 'A1',
      productId: 'P1',
      scene: 'PRODUCT_DETAIL',
      convertLink,
      writeText
    })

    expect(writeText).toHaveBeenCalledOnce()
    expect(writeText.mock.calls[0][0]).toContain('【商品】转链失败商品（测试店铺）')
    expect(writeText.mock.calls[0][0]).not.toContain('【链接】')
    expect(result.link).toBeNull()
    expect(result.linkGenerationFailed).toBe(true)
  })
})
