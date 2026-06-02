import { describe, expect, it } from 'vitest'
import {
  buildApproveProductPayload,
  createAuditSupplementForm,
  validateApprovalSupplement
} from './product-audit-supplement'

describe('product audit supplement', () => {
  it('builds approve payload with required supplement fields', () => {
    const form = createAuditSupplementForm({
      auditSupplement: {
        exclusivePriceRemark: ' 直播间专属价 129 元 ',
        shippingInfo: '48 小时内发货',
        sellingPoints: ['高复购', '  ', '夏季场景强'],
        promotionScript: '主打囤货场景',
        supportsAds: false,
        rewardRemark: '破 3 万 GMV 额外奖励',
        participationRequirements: '近 30 天有成交记录',
        campaignTimeRemark: '6 月 1 日至 6 月 15 日',
        materialFiles: [' https://example.com/card.png ']
      }
    })

    expect(buildApproveProductPayload(' 素材完整 ', form)).toEqual({
      remark: '素材完整',
      exclusivePriceRemark: '直播间专属价 129 元',
      shippingInfo: '48 小时内发货',
      sellingPoints: ['高复购', '夏季场景强'],
      promotionScript: '主打囤货场景',
      supportsAds: false,
      adsRule: '',
      rewardRemark: '破 3 万 GMV 额外奖励',
      participationRequirements: '近 30 天有成交记录',
      campaignTimeRemark: '6 月 1 日至 6 月 15 日',
      materialFiles: ['https://example.com/card.png']
    })
  })

  it('reports missing labels before approve submit', () => {
    const missing = validateApprovalSupplement(createAuditSupplementForm(null))

    expect(missing).toEqual([
      '专属价说明',
      '发货信息',
      '商品卖点',
      '推广话术',
      '奖励说明',
      '参与要求',
      '活动时间',
      '手卡素材'
    ])
  })

  it('prefills campaign time and hand card from row fallback fields', () => {
    const form = createAuditSupplementForm({
      promotionStartTime: '2026-06-01',
      promotionEndTime: '2026-06-15',
      handCardUrl: 'https://example.com/fallback-card.png'
    })

    expect(form.campaignTimeRemark).toBe('2026-06-01 - 2026-06-15')
    expect(form.materialFiles).toBe('https://example.com/fallback-card.png')
  })
})
