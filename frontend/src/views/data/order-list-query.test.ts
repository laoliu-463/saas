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
        shopName: '测试店铺',
        productId: 'P-1',
        productName: '测试商品',
        talentName: '达人甲',
        colonelName: '招商甲',
        channelName: '渠道乙',
        recruitType: 'PROMOTION'
      },
      timeField: 'settleTime',
      dateRange: null
    })

    expect(params).toEqual({
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: 'TALENT-1',
      merchantId: undefined,
      colonelActivityId: 'ACT-1',
      shopName: '测试店铺',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      colonelName: '招商甲',
      channelName: '渠道乙',
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
        shopName: '',
        productId: '',
        productName: '',
        talentName: '',
        colonelName: '',
        channelName: '',
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
      colonelActivityId: undefined,
      shopName: undefined,
      productId: undefined,
      productName: undefined,
      talentName: undefined,
      colonelName: undefined,
      channelName: undefined,
      recruitType: undefined,
      timeField: 'createTime',
      startDate: '2026-05-01',
      endDate: '2026-05-03'
    })
  })
})
