import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  batchGetPerformance,
  exportPerformance,
  getExclusiveMerchant,
  getMyExclusiveMerchants,
  getPerformance,
  getPerformanceSummary,
  listPerformance,
  recalculatePerformanceMonth
} from './performance'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('performance domain API', () => {
  it('calls single order performance endpoint', () => {
    getPerformance('ORDER-1')

    expect(request.get).toHaveBeenCalledWith('/performance/ORDER-1')
  })

  it('calls batch performance endpoint', () => {
    batchGetPerformance(['ORDER-1', 'ORDER-2'])

    expect(request.post).toHaveBeenCalledWith('/performance/batch', {
      orderIds: ['ORDER-1', 'ORDER-2']
    })
  })

  it('calls performance list and summary endpoints', () => {
    listPerformance({ page: 2, pageSize: 50 })
    getPerformanceSummary({ timeFilterType: 'pay' })

    expect(request.get).toHaveBeenCalledWith('/performance', { params: { page: 2, pageSize: 50 } })
    expect(request.get).toHaveBeenCalledWith('/performance/summary', { params: { timeFilterType: 'pay' } })
  })

  it('exports performance as xlsx blob', () => {
    exportPerformance({ orderStatus: 'FINISHED' })

    expect(request.get).toHaveBeenCalledWith('/performance/export', {
      params: { orderStatus: 'FINISHED' },
      responseType: 'blob'
    })
  })

  it('calls exclusive merchant and recalculation endpoints', () => {
    getExclusiveMerchant('partner-1')
    getMyExclusiveMerchants()
    recalculatePerformanceMonth({ month: '2026-05', reason: '修正提成比例后重算' })

    expect(request.get).toHaveBeenCalledWith('/exclusive-merchants/partner-1')
    expect(request.get).toHaveBeenCalledWith('/exclusive-merchants/my')
    expect(request.post).toHaveBeenCalledWith('/performance/recalculate-month', {
      month: '2026-05',
      reason: '修正提成比例后重算'
    })
  })
})
