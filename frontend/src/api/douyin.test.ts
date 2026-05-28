import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  createDouyinToken,
  createOrUpdateDouyinActivity,
  getDouyinActivityDetail,
  getDouyinActivityProductList,
  getDouyinActivityTest,
  getDouyinAuthorizeUrl,
  getDouyinInstitutionInfo,
  getDouyinOrderSettlements,
  getDouyinProductActivities,
  getDouyinProductsByActivity,
  getDouyinTokenStatus,
  postDouyinRawProbe,
  refreshDouyinToken
} from './douyin'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

describe('douyin API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getDouyinTokenStatus', () => {
    it('calls token status endpoint without appId', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { hasAccessToken: false } })

      await getDouyinTokenStatus()

      expect(requestGet).toHaveBeenCalledWith('/douyin/tokens', {
        params: { appId: undefined },
        timeout: 120_000
      })
    })

    it('calls token status endpoint with appId', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { hasAccessToken: true } })

      await getDouyinTokenStatus('app-123')

      expect(requestGet).toHaveBeenCalledWith('/douyin/tokens', {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
    })

    it('handles 401 reauthorize required error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getDouyinTokenStatus('app-123')).rejects.toThrow('401 Unauthorized')
    })

    it('handles network failure error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getDouyinTokenStatus()).rejects.toThrow('Network Error')
    })

    it('handles timeout error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 120000ms exceeded'))

      await expect(getDouyinTokenStatus()).rejects.toThrow('timeout of 120000ms exceeded')
    })
  })

  describe('refreshDouyinToken', () => {
    it('calls token refresh endpoint with appId', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockResolvedValueOnce({ data: { hasAccessToken: true } })

      await refreshDouyinToken('app-123')

      expect(requestPost).toHaveBeenCalledWith('/douyin/token-refreshes', null, {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
    })

    it('handles refresh failure error', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(refreshDouyinToken('app-123')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('createDouyinToken', () => {
    it('calls create token endpoint with authorization code', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockResolvedValueOnce({ data: { accessToken: 'new-token' } })

      await createDouyinToken({ code: 'auth-code-123', grantType: 'authorization_code' })

      expect(requestPost).toHaveBeenCalledWith('/douyin/tokens', {
        code: 'auth-code-123',
        grantType: 'authorization_code'
      }, { timeout: 120_000 })
    })

    it('handles token creation error (400 Bad Request)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('400 Bad Request'))

      await expect(createDouyinToken({ code: 'invalid' })).rejects.toThrow('400 Bad Request')
    })

    it('handles network failure error', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(createDouyinToken({})).rejects.toThrow('Network Error')
    })
  })

  describe('getDouyinAuthorizeUrl', () => {
    it('calls authorize-url endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({
        data: { authorizeUrl: 'https://example.com/auth', state: 's1' }
      })

      const result = await getDouyinAuthorizeUrl('app-123')

      expect(requestGet).toHaveBeenCalledWith('/douyin/oauth/authorize-url', {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
      expect(result.authorizeUrl).toBe('https://example.com/auth')
    })

    it('handles oauth error (401)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getDouyinAuthorizeUrl('app-123')).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('getDouyinActivityTest', () => {
    it('calls activity test endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { status: 'success' } })

      await getDouyinActivityTest('app-123')

      expect(requestGet).toHaveBeenCalledWith('/douyin/activities', {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
    })

    it('handles activity test error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getDouyinActivityTest('app-123')).rejects.toThrow('500 Internal Server Error')
    })

    it('handles timeout error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 120000ms exceeded'))

      await expect(getDouyinActivityTest('app-123')).rejects.toThrow('timeout of 120000ms exceeded')
    })
  })

  describe('getDouyinActivityDetail', () => {
    it('calls activity detail endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { activityId: 'ACT-1', name: '测试活动' } })

      const result = await getDouyinActivityDetail('app-123', 'ACT-1')

      expect(requestGet).toHaveBeenCalledWith('/douyin/activities/ACT-1', {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
      expect(result.activityId).toBe('ACT-1')
    })

    it('handles activity not found error (404)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('404 Not Found'))

      await expect(getDouyinActivityDetail('app-123', 'INVALID')).rejects.toThrow('404 Not Found')
    })

    it('handles 401 unauthorized error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getDouyinActivityDetail('app-123', 'ACT-1')).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('getDouyinInstitutionInfo', () => {
    it('calls institution info endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { name: '机构名称' } })

      await getDouyinInstitutionInfo('app-123')

      expect(requestGet).toHaveBeenCalledWith('/douyin/institution-info', {
        params: { appId: 'app-123' },
        timeout: 120_000
      })
    })

    it('handles institution info error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getDouyinInstitutionInfo('app-123')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getDouyinProductActivities', () => {
    it('calls product activities endpoint with params', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { products: [] } })

      await getDouyinProductActivities({ page: 1, size: 20 })

      expect(requestGet).toHaveBeenCalledWith('/douyin/activity-products', {
        params: { page: 1, size: 20 },
        timeout: 120_000
      })
    })

    it('handles product activities error (network failure)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getDouyinProductActivities({})).rejects.toThrow('Network Error')
    })
  })

  describe('getDouyinActivityProductList', () => {
    it('calls activity product list endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { items: [] } })

      await getDouyinActivityProductList({ appId: 'app-123', activityId: 'ACT-1', count: 10 })

      expect(requestGet).toHaveBeenCalledWith('/douyin/activity-product-list', {
        params: { appId: 'app-123', activityId: 'ACT-1', count: 10 },
        timeout: 120_000
      })
    })

    it('handles activity product list error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getDouyinActivityProductList({ activityId: 'ACT-1' })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getDouyinOrderSettlements', () => {
    it('calls order settlements endpoint with time range', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { settlements: [] } })

      await getDouyinOrderSettlements({
        appId: 'app-123',
        timeType: 'settle',
        startTime: '2026-05-01',
        endTime: '2026-05-28'
      })

      expect(requestGet).toHaveBeenCalledWith('/douyin/order-settlements', {
        params: {
          appId: 'app-123',
          timeType: 'settle',
          startTime: '2026-05-01',
          endTime: '2026-05-28'
        },
        timeout: 120_000
      })
    })

    it('handles order settlements error (401)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getDouyinOrderSettlements({})).rejects.toThrow('401 Unauthorized')
    })

    it('handles order settlements error (timeout)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 120000ms exceeded'))

      await expect(getDouyinOrderSettlements({})).rejects.toThrow('timeout of 120000ms exceeded')
    })
  })

  describe('getDouyinProductsByActivity', () => {
    it('calls products by activity endpoint', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: { products: [] } })

      await getDouyinProductsByActivity({ appId: 'app-123', activityId: 'ACT-1', count: 20 })

      expect(requestGet).toHaveBeenCalledWith('/douyin/activities/ACT-1/products', {
        params: { appId: 'app-123', count: 20 },
        timeout: 120_000
      })
    })

    it('handles products by activity error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getDouyinProductsByActivity({ activityId: 'ACT-1' })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles network failure error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getDouyinProductsByActivity({ activityId: 'ACT-1' })).rejects.toThrow('Network Error')
    })
  })

  describe('postDouyinRawProbe', () => {
    it('calls raw probe endpoint with data', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockResolvedValueOnce({ data: { status: 'success' } })

      await postDouyinRawProbe({ url: 'https://example.com/product', mode: 'test' })

      expect(requestPost).toHaveBeenCalledWith('/douyin/promotion-link-probes/raw', {
        url: 'https://example.com/product',
        mode: 'test'
      }, { timeout: 120_000 })
    })

    it('handles raw probe error (400 Bad Request)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('400 Bad Request'))

      await expect(postDouyinRawProbe({})).rejects.toThrow('400 Bad Request')
    })

    it('handles raw probe error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(postDouyinRawProbe({ url: 'test' })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('createOrUpdateDouyinActivity', () => {
    it('creates new activity (POST) when no activityId', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockResolvedValueOnce({ data: { activityId: 'NEW-1' } })

      await createOrUpdateDouyinActivity({ name: '新活动', startTime: '2026-06-01' })

      expect(requestPost).toHaveBeenCalledWith('/douyin/activities', {
        name: '新活动',
        startTime: '2026-06-01'
      }, { timeout: 120_000 })
    })

    it('updates existing activity (PUT) when activityId provided', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockResolvedValueOnce({ data: { activityId: 'ACT-1', updated: true } })

      await createOrUpdateDouyinActivity({ activityId: 'ACT-1', name: '更新后的活动' })

      expect(requestPut).toHaveBeenCalledWith('/douyin/activities/ACT-1', {
        activityId: 'ACT-1',
        name: '更新后的活动'
      }, { timeout: 120_000 })
    })

    it('handles create error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(createOrUpdateDouyinActivity({ name: 'test' })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles update error (401)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(createOrUpdateDouyinActivity({ activityId: 'ACT-1', name: 'test' })).rejects.toThrow('401 Unauthorized')
    })

    it('handles network failure for create', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(createOrUpdateDouyinActivity({ name: 'test' })).rejects.toThrow('Network Error')
    })

    it('handles timeout for update', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('timeout of 120000ms exceeded'))

      await expect(createOrUpdateDouyinActivity({ activityId: 'ACT-1', name: 'test' })).rejects.toThrow('timeout of 120000ms exceeded')
    })
  })
})
