import { describe, expect, it } from 'vitest'

import { buildOrderExportParams, buildOrderPageParams } from './order-list-query'

describe('order list query helpers', () => {
  it('keeps export filters aligned with the list filters', () => {
    const params = buildOrderExportParams({
      filters: {
        orderId: 'ORDER-7788',
        status: 'FINISHED',
        talentId: 'TALENT-1',
        merchantId: ''
      },
      timeField: 'settleTime',
      dateRange: null
    })

    expect(params).toEqual({
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: 'TALENT-1',
      merchantId: undefined,
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
        merchantId: 'merchant_10086'
      },
      timeField: 'createTime',
      dateRange: [Date.UTC(2026, 4, 1), Date.UTC(2026, 4, 3)]
    })

    expect(params).toEqual({
      page: 2,
      size: 50,
      orderId: 'ORDER-7788',
      status: null,
      talentId: undefined,
      merchantId: 'merchant_10086',
      timeField: 'createTime',
      startDate: '2026-05-01',
      endDate: '2026-05-03'
    })
  })
})
