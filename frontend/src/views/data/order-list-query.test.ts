import { describe, expect, it } from 'vitest'

import { buildOrderExportParams, buildOrderPageParams } from './order-list-query'

describe('order list query helpers', () => {
  it('keeps export filters aligned with the list filters', () => {
    const params = buildOrderExportParams({
      filters: {
        orderId: 'ORDER-7788',
        status: 'FINISHED',
        talentId: 'TALENT-1',
        merchantId: '',
        activityId: 'ACT-1',
        activityName: '活动甲',
        shopName: '测试店铺',
        partnerId: 'PARTNER-1',
        partnerName: '合作方甲',
        productId: 'P-1',
        productName: '测试商品',
        talentName: '达人甲',
        colonelName: '招商甲',
        channelName: '渠道乙',
        recruiterName: '招商乙',
        recruitType: 'PROMOTION'
      },
      timeField: 'settleTime',
      dateRange: null
    })

    expect(params).toEqual({
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: 'TALENT-1',
      merchantId: 'PARTNER-1',
      partnerId: 'PARTNER-1',
      colonelActivityId: 'ACT-1',
      activityName: '活动甲',
      shopName: '合作方甲',
      partnerName: '合作方甲',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      colonelName: '招商乙',
      channelName: '渠道乙',
      recruiterName: '招商乙',
      recruitType: 'PROMOTION',
      timeField: 'settleTime',
      startDate: undefined,
      endDate: undefined
    })
  })

  it('builds page query params with pagination and the same filter names', () => {
    const params = buildOrderPageParams({
      page: 2,
      pageSize: 50,
      filters: {
        orderId: 'ORDER-7788',
        status: null,
        talentId: '',
        merchantId: 'merchant_10086',
        activityId: '',
        activityName: '',
        shopName: '',
        partnerId: '',
        partnerName: '',
        productId: '',
        productName: '',
        talentName: '',
        colonelName: '',
        channelName: '',
        recruiterName: null,
        recruitType: null
      },
      timeField: 'createTime',
      dateRange: [Date.UTC(2026, 4, 1), Date.UTC(2026, 4, 3)]
    })

    expect(params).toEqual({
      page: 2,
      size: 50,
      orderId: 'ORDER-7788',
      status: undefined,
      talentId: undefined,
      merchantId: 'merchant_10086',
      partnerId: undefined,
      colonelActivityId: undefined,
      activityName: undefined,
      shopName: undefined,
      partnerName: undefined,
      productId: undefined,
      productName: undefined,
      talentName: undefined,
      colonelName: undefined,
      channelName: undefined,
      recruiterName: undefined,
      recruitType: undefined,
      timeField: 'createTime',
      startDate: '2026-05-01',
      endDate: '2026-05-03'
    })
  })
})
