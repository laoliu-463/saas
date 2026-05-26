import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  exportActivities,
  exportOrders,
  getExclusiveMerchantStatus,
  getExclusiveTalentStatus,
  getMetrics,
  getOrderPage,
  getOrderSummary
} from './data'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

describe('data API', () => {
  it('calls order list and summary endpoints with params', () => {
    const params = { timeField: 'createTime', startDate: '2026-05-25', endDate: '2026-05-31' }

    getOrderPage(params)
    getOrderSummary(params)

    expect(request.get).toHaveBeenCalledWith('/data/orders', { params })
    expect(request.get).toHaveBeenCalledWith('/data/orders/summary', { params })
  })

  it('keeps data exports as blob downloads', () => {
    exportOrders({ orderId: 'ORDER-1' })
    exportActivities({ activityId: 'ACT-1' })

    expect(request.get).toHaveBeenCalledWith('/orders/exports', {
      params: { orderId: 'ORDER-1' },
      responseType: 'blob'
    })
    expect(request.get).toHaveBeenCalledWith('/activities/exports', {
      params: { activityId: 'ACT-1' },
      responseType: 'blob'
    })
  })

  it('calls dashboard and exclusive status endpoints', () => {
    getMetrics({ timeField: 'settleTime' }, { signal: 'test-signal' })
    getExclusiveTalentStatus()
    getExclusiveMerchantStatus()

    expect(request.get).toHaveBeenCalledWith('/dashboard/metrics', {
      params: { timeField: 'settleTime' },
      signal: 'test-signal'
    })
    expect(request.get).toHaveBeenCalledWith('/operations/exclusive-talents')
    expect(request.get).toHaveBeenCalledWith('/operations/exclusive-merchants')
  })
})
