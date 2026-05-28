import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { getSummary } from './dashboard'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

describe('dashboard API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSummary', () => {
    it('calls dashboard summary endpoint', () => {
      getSummary()

      expect(request.get).toHaveBeenCalledWith('/dashboard/summary', { params: undefined })
    })

    it('calls dashboard summary endpoint with params', () => {
      const params = { startDate: '2026-05-01', endDate: '2026-05-31' }

      getSummary(params)

      expect(request.get).toHaveBeenCalledWith('/dashboard/summary', { params })
    })

    it('handles network failure error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getSummary()).rejects.toThrow('Network Error')
    })

    it('handles 401 Unauthorized error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getSummary()).rejects.toThrow('401 Unauthorized')
    })

    it('handles 500 Server Error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getSummary()).rejects.toThrow('500 Internal Server Error')
    })

    it('handles timeout error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(getSummary()).rejects.toThrow('timeout of 30000ms exceeded')
    })

    it('passes complex filter params to summary endpoint', () => {
      const params = {
        dimension: 'user',
        userId: 'USER-1',
        timeField: 'settleTime',
        startDate: '2026-01-01',
        endDate: '2026-05-28'
      }

      getSummary(params)

      expect(request.get).toHaveBeenCalledWith('/dashboard/summary', { params })
    })
  })
})
