import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  getOrderDetail,
  getOrderFilterOptions,
  getOrders,
  getOrderStats,
  getUnattributedOrders,
  syncOrders,
  triggerOrderSync
} from './order'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('order API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('syncOrders', () => {
    it('calls order sync endpoint with time range', () => {
      syncOrders('2026-05-01 00:00:00', '2026-05-28 23:59:59')

      expect(request.post).toHaveBeenCalledWith('/orders/sync', {
        startTime: '2026-05-01 00:00:00',
        endTime: '2026-05-28 23:59:59'
      }, {})
    })

    it('passes config options to sync request', () => {
      const config = { timeout: 60000 }

      syncOrders('2026-05-01 00:00:00', '2026-05-28 23:59:59', config)

      expect(request.post).toHaveBeenCalledWith('/orders/sync', {
        startTime: '2026-05-01 00:00:00',
        endTime: '2026-05-28 23:59:59'
      }, config)
    })

    it('handles sync error (network failure)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(syncOrders('2026-05-01 00:00:00', '2026-05-28 23:59:59')).rejects.toThrow('Network Error')
    })

    it('handles sync error (500 Server Error)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(syncOrders('2026-05-01 00:00:00', '2026-05-28 23:59:59')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getOrders', () => {
    it('calls order list endpoint with params', () => {
      const params = { page: 1, size: 20 }

      getOrders(params)

      expect(request.get).toHaveBeenCalledWith('/orders', { params, ...{} })
    })

    it('passes filter conditions to order list', () => {
      const params = {
        page: 1,
        size: 10,
        attributionStatus: 'ATTRIBUTED',
        productName: '测试商品'
      }

      getOrders(params)

      expect(request.get).toHaveBeenCalledWith('/orders', { params, ...{} })
    })

    it('passes config options to get request', () => {
      const params = { page: 1, size: 20 }
      const config = { signal: new AbortController().signal }

      getOrders(params, config)

      expect(request.get).toHaveBeenCalledWith('/orders', { params, ...config })
    })

    it('handles 401 Unauthorized error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getOrders({ page: 1 })).rejects.toThrow('401 Unauthorized')
    })

    it('handles network timeout error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(getOrders({ page: 1 })).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('getUnattributedOrders', () => {
    it('calls unattributed orders endpoint', () => {
      getUnattributedOrders({ page: 1, size: 20 })

      expect(request.get).toHaveBeenCalledWith('/orders/unattributed', {
        params: { page: 1, size: 20 }
      })
    })

    it('passes filter conditions for unattributed orders', () => {
      const params = {
        page: 1,
        size: 50,
        diagnosisReasonCode: 'NO_PROMOTION_LINK'
      }

      getUnattributedOrders(params)

      expect(request.get).toHaveBeenCalledWith('/orders/unattributed', { params })
    })

    it('handles unattributed orders error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getUnattributedOrders({ page: 1 })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getOrderStats', () => {
    it('calls order stats endpoint without params', () => {
      getOrderStats()

      expect(request.get).toHaveBeenCalledWith('/orders/stats', { params: undefined })
    })

    it('calls order stats endpoint with params', () => {
      const params = { startDate: '2026-05-01', endDate: '2026-05-28' }

      getOrderStats(params)

      expect(request.get).toHaveBeenCalledWith('/orders/stats', { params })
    })

    it('handles stats error (network failure)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getOrderStats()).rejects.toThrow('Network Error')
    })

    it('handles stats error (timeout)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(getOrderStats()).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('getOrderFilterOptions', () => {
    it('calls filter options endpoint without params', () => {
      getOrderFilterOptions()

      expect(request.get).toHaveBeenCalledWith('/orders/filter-options', { params: undefined })
    })

    it('calls filter options endpoint with params', () => {
      const params = { activityId: 'ACT-1' }

      getOrderFilterOptions(params)

      expect(request.get).toHaveBeenCalledWith('/orders/filter-options', { params })
    })

    it('handles filter options error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getOrderFilterOptions()).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getOrderDetail', () => {
    it('calls order detail endpoint and extracts data', async () => {
      const requestGet = vi.mocked(request.get)
      const mockDetail = {
        orderId: 'ORDER-123',
        orderStatus: 1,
        orderStatusText: '已完成'
      }
      requestGet.mockResolvedValueOnce({ data: mockDetail })

      const result = await getOrderDetail('ORDER-123')

      expect(requestGet).toHaveBeenCalledWith('/orders/ORDER-123')
      expect(result).toEqual(mockDetail)
    })

    it('handles order detail error (not found)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('404 Not Found'))

      await expect(getOrderDetail('ORDER-INVALID')).rejects.toThrow('404 Not Found')
    })

    it('handles order detail error (401)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getOrderDetail('ORDER-123')).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('triggerOrderSync (deprecated)', () => {
    it('calls syncOrders with calculated date range (30 days)', () => {
      const now = new Date('2026-05-28T12:00:00Z')
      vi.setSystemTime(now)

      triggerOrderSync()

      // Should call syncOrders with dates approximately 30 days apart
      const callArgs = vi.mocked(request.post).mock.calls[0]
      expect(callArgs[0]).toBe('/orders/sync')
      expect(callArgs[1]).toHaveProperty('startTime')
      expect(callArgs[1]).toHaveProperty('endTime')
    })

    it('handles deprecated sync error', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(triggerOrderSync()).rejects.toThrow('500 Internal Server Error')
    })
  })
})
