import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  assignActivityProduct,
  assignActivityProductAuditOwner,
  auditActivityProduct,
  batchAssignActivityProducts,
  batchPinActivityProducts,
  batchPutActivityProductsIntoLibrary,
  convertActivityProductLink,
  followActivityProduct,
  getActivityProductDetail,
  getActivityProductOperationLogs,
  getActivityProductSyncJob,
  getActivityProductSkus,
  getActivityProducts,
  getPinnedProducts,
  pinActivityProduct,
  putActivityProductIntoLibrary,
  syncActivityProducts,
  unpinActivityProduct,
  updateActivityProductDecision
} from './activityProduct'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

describe('activityProduct API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getActivityProducts', () => {
    it('calls activity products list endpoint', () => {
      getActivityProducts('ACT-1', { page: 1, size: 20 })

      expect(request.get).toHaveBeenCalledWith('/colonel/activities/ACT-1/products', {
        timeout: 30000,
        params: { page: 1, size: 20 },
        ...{}
      })
    })

    it('passes filter params to activity products list', () => {
      getActivityProducts('ACT-1', { page: 1, size: 10, decisionLevel: 'MAIN' })

      expect(request.get).toHaveBeenCalledWith('/colonel/activities/ACT-1/products', {
        timeout: 30000,
        params: { page: 1, size: 10, decisionLevel: 'MAIN' },
        ...{}
      })
    })

    it('passes config options', () => {
      const config = { signal: new AbortController().signal }
      getActivityProducts('ACT-1', { page: 1 }, config)

      expect(request.get).toHaveBeenCalledWith('/colonel/activities/ACT-1/products', {
        timeout: 30000,
        params: { page: 1 },
        ...config
      })
    })

    it('handles network failure error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('Network Error'))

      await expect(getActivityProducts('ACT-1', { page: 1 })).rejects.toThrow('Network Error')
    })

    it('handles 401 Unauthorized error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getActivityProducts('ACT-1', { page: 1 })).rejects.toThrow('401 Unauthorized')
    })

    it('handles 500 Server Error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getActivityProducts('ACT-1', { page: 1 })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles timeout error', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(getActivityProducts('ACT-1', { page: 1 })).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('getActivityProductDetail', () => {
    it('calls activity product detail endpoint', () => {
      getActivityProductDetail('ACT-1', 'PROD-1')

      expect(request.get).toHaveBeenCalledWith('/colonel/activities/ACT-1/products/PROD-1')
    })

    it('handles detail not found error (404)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('404 Not Found'))

      await expect(getActivityProductDetail('ACT-1', 'INVALID')).rejects.toThrow('404 Not Found')
    })
  })

  describe('getActivityProductSkus', () => {
    it('calls activity product SKUs endpoint', () => {
      getActivityProductSkus('ACT-1', 'PROD-1')

      expect(request.get).toHaveBeenCalledWith('/colonel/activities/ACT-1/products/PROD-1/skus')
    })

    it('handles SKUs error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getActivityProductSkus('ACT-1', 'PROD-1')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('auditActivityProduct', () => {
    it('calls audit endpoint with approved=true', () => {
      const data = { approved: true, sellingPoints: ['卖点1', '卖点2'] }

      auditActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/audit-result',
        data
      )
    })

    it('calls audit endpoint with approved=false and reason', () => {
      const data = { approved: false, reason: '价格不合适' }

      auditActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/audit-result',
        data
      )
    })

    it('passes full audit data including sample thresholds', () => {
      const data = {
        approved: true,
        exclusivePriceRemark: '独家价格备注',
        shippingInfo: '运费说明',
        sellingPoints: ['卖点'],
        promotionScript: '推广话术',
        supportsAds: true,
        adsRule: '投广规则',
        rewardRemark: '奖励备注',
        participationRequirements: '参与要求',
        campaignTimeRemark: '活动时间备注',
        materialFiles: ['file1.pdf'],
        goodsTags: ['tag1'],
        productTags: ['productTag1'],
        sampleThresholdSales: 1000,
        sampleThresholdLevel: 3,
        sampleThresholdRemark: '寄样备注'
      }

      auditActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/audit-result',
        data
      )
    })

    it('handles audit error (400 Bad Request)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('400 Bad Request'))

      await expect(auditActivityProduct('ACT-1', 'PROD-1', { approved: false })).rejects.toThrow('400 Bad Request')
    })

    it('handles audit error (500)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(auditActivityProduct('ACT-1', 'PROD-1', { approved: true })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('assignActivityProduct', () => {
    it('calls assign endpoint with assigneeId', () => {
      const data = { assigneeId: 'USER-1' }

      assignActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/assignee',
        data
      )
    })

    it('handles assign error (401)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(assignActivityProduct('ACT-1', 'PROD-1', { assigneeId: 'USER-1' })).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('batchAssignActivityProducts', () => {
    it('calls batch assign endpoint', () => {
      const data = { productIds: ['PROD-1', 'PROD-2'], assigneeId: 'USER-1' }

      batchAssignActivityProducts('ACT-1', data)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/batch-assign',
        data
      )
    })

    it('handles batch assign error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(batchAssignActivityProducts('ACT-1', { productIds: ['P1'], assigneeId: 'U1' })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('assignActivityProductAuditOwner', () => {
    it('calls audit assignee endpoint', () => {
      const data = { assigneeId: 'AUDITOR-1' }

      assignActivityProductAuditOwner('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/audit-assignee',
        data
      )
    })

    it('handles audit assignee error (400)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('400 Bad Request'))

      await expect(assignActivityProductAuditOwner('ACT-1', 'PROD-1', { assigneeId: 'INVALID' })).rejects.toThrow('400 Bad Request')
    })
  })

  describe('updateActivityProductDecision', () => {
    it('calls decision update endpoint with MAIN level', () => {
      const data = { decisionLevel: 'MAIN' as const, reason: '主推商品' }

      updateActivityProductDecision('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/decision',
        data
      )
    })

    it('calls decision update endpoint with DROP level', () => {
      const data = { decisionLevel: 'DROP' as const, reason: '停止运营' }

      updateActivityProductDecision('ACT-1', 'PROD-1', data)

      expect(request.put).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/decision',
        data
      )
    })

    it('handles decision update error (500)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(updateActivityProductDecision('ACT-1', 'PROD-1', { decisionLevel: 'PAUSE', reason: 'test' })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles decision update error (timeout)', async () => {
      const requestPut = vi.mocked(request.put)
      requestPut.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(updateActivityProductDecision('ACT-1', 'PROD-1', { decisionLevel: 'MAIN', reason: 'test' })).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('convertActivityProductLink', () => {
    it('calls promotion link endpoint with default scene', () => {
      convertActivityProductLink('ACT-1', 'PROD-1')

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/promotion-links',
        { scene: 'PRODUCT_LIBRARY' },
        undefined
      )
    })

    it('calls promotion link endpoint with custom scene', () => {
      convertActivityProductLink('ACT-1', 'PROD-1', { scene: 'TALENT_SHARE', talentId: 'TALENT-1' })

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/promotion-links',
        { scene: 'TALENT_SHARE', talentId: 'TALENT-1' },
        undefined
      )
    })

    it('passes config options with suppressErrorNotice', () => {
      const config = { suppressErrorNotice: true }

      convertActivityProductLink('ACT-1', 'PROD-1', undefined, config)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/promotion-links',
        { scene: 'PRODUCT_LIBRARY' },
        config
      )
    })

    it('handles conversion error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(convertActivityProductLink('ACT-1', 'PROD-1')).rejects.toThrow('500 Internal Server Error')
    })

    it('handles conversion error (network failure)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(convertActivityProductLink('ACT-1', 'PROD-1')).rejects.toThrow('Network Error')
    })
  })

  describe('putActivityProductIntoLibrary', () => {
    it('calls library entry endpoint', () => {
      putActivityProductIntoLibrary('ACT-1', 'PROD-1')

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/library-entry'
      )
    })

    it('handles library entry error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(putActivityProductIntoLibrary('ACT-1', 'PROD-1')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('batchPutActivityProductsIntoLibrary', () => {
    it('calls batch library entry endpoint', () => {
      const data = { productIds: ['PROD-1', 'PROD-2'] }

      batchPutActivityProductsIntoLibrary('ACT-1', data)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/batch-library-entry',
        data
      )
    })

    it('handles batch library entry error (400)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('400 Bad Request'))

      await expect(batchPutActivityProductsIntoLibrary('ACT-1', { productIds: [] })).rejects.toThrow('400 Bad Request')
    })
  })

  describe('pinActivityProduct', () => {
    it('calls pin endpoint', () => {
      pinActivityProduct('ACT-1', 'PROD-1')

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/pin'
      )
    })

    it('handles pin error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(pinActivityProduct('ACT-1', 'PROD-1')).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('unpinActivityProduct', () => {
    it('calls unpin endpoint', () => {
      unpinActivityProduct('ACT-1', 'PROD-1')

      expect(request.delete).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/pin'
      )
    })

    it('handles unpin error (401)', async () => {
      const requestDelete = vi.mocked(request.delete)
      requestDelete.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(unpinActivityProduct('ACT-1', 'PROD-1')).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('batchPinActivityProducts', () => {
    it('calls batch pin endpoint', () => {
      const data = { productIds: ['PROD-1', 'PROD-2'] }

      batchPinActivityProducts('ACT-1', data)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/batch-pin',
        data
      )
    })

    it('handles batch pin error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(batchPinActivityProducts('ACT-1', { productIds: ['P1'] })).rejects.toThrow('500 Internal Server Error')
    })
  })

  describe('getPinnedProducts', () => {
    it('calls pinned products endpoint and extracts data', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockResolvedValueOnce({ data: [{ productId: 'PROD-1' }] })

      const result = await getPinnedProducts()

      expect(requestGet).toHaveBeenCalledWith('/colonel/pinned-products')
      expect(result).toEqual([{ productId: 'PROD-1' }])
    })

    it('handles pinned products error (401)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getPinnedProducts()).rejects.toThrow('401 Unauthorized')
    })

    it('handles pinned products error (timeout)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(getPinnedProducts()).rejects.toThrow('timeout of 30000ms exceeded')
    })
  })

  describe('followActivityProduct', () => {
    it('calls follow endpoint with follow data', () => {
      const data = {
        followStatus: 'FOLLOWING',
        content: '跟进内容',
        nextFollowTime: '2026-06-01 10:00:00',
        operatorName: '张三'
      }

      followActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/follow',
        data
      )
    })

    it('calls follow endpoint with talent info', () => {
      const data = {
        followStatus: 'CONTACTED',
        talentId: 'TALENT-1',
        talentName: '达人张三',
        content: '已联系达人',
        operatorName: '李四'
      }

      followActivityProduct('ACT-1', 'PROD-1', data)

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/follow',
        data
      )
    })

    it('handles follow error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(followActivityProduct('ACT-1', 'PROD-1', { followStatus: 'PENDING' })).rejects.toThrow('500 Internal Server Error')
    })

    it('handles follow error (network failure)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(followActivityProduct('ACT-1', 'PROD-1', { followStatus: 'PENDING' })).rejects.toThrow('Network Error')
    })
  })

  describe('getActivityProductOperationLogs', () => {
    it('calls operation logs endpoint without params', () => {
      getActivityProductOperationLogs('ACT-1', 'PROD-1')

      expect(request.get).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/operation-logs',
        { params: undefined }
      )
    })

    it('calls operation logs endpoint with pagination params', () => {
      getActivityProductOperationLogs('ACT-1', 'PROD-1', { page: 1, size: 50 })

      expect(request.get).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/PROD-1/operation-logs',
        { params: { page: 1, size: 50 } }
      )
    })

    it('handles operation logs error (500)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(getActivityProductOperationLogs('ACT-1', 'PROD-1')).rejects.toThrow('500 Internal Server Error')
    })

    it('handles operation logs error (401)', async () => {
      const requestGet = vi.mocked(request.get)
      requestGet.mockRejectedValueOnce(new Error('401 Unauthorized'))

      await expect(getActivityProductOperationLogs('ACT-1', 'PROD-1')).rejects.toThrow('401 Unauthorized')
    })
  })

  describe('syncActivityProducts', () => {
    it('calls sync activity products endpoint', () => {
      syncActivityProducts('ACT-1')

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/sync',
        undefined,
        { timeout: 30000 }
      )
    })

    it('passes priority sync options to sync endpoint', () => {
      syncActivityProducts('ACT-1', {
        syncMode: 'PRIORITY_1000',
        maxRowsPerActivity: 1000,
        priorityStatuses: [0, 1]
      })

      expect(request.post).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/sync',
        {
          syncMode: 'PRIORITY_1000',
          maxRowsPerActivity: 1000,
          priorityStatuses: [0, 1]
        },
        { timeout: 30000 }
      )
    })

    it('handles sync error (500)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('500 Internal Server Error'))

      await expect(syncActivityProducts('ACT-1')).rejects.toThrow('500 Internal Server Error')
    })

    it('handles sync error (timeout)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('timeout of 30000ms exceeded'))

      await expect(syncActivityProducts('ACT-1')).rejects.toThrow('timeout of 30000ms exceeded')
    })

    it('handles sync error (network failure)', async () => {
      const requestPost = vi.mocked(request.post)
      requestPost.mockRejectedValueOnce(new Error('Network Error'))

      await expect(syncActivityProducts('ACT-1')).rejects.toThrow('Network Error')
    })
  })

  describe('getActivityProductSyncJob', () => {
    it('calls activity product sync job status endpoint', () => {
      getActivityProductSyncJob('ACT-1', 'JOB-1', { suppressErrorNotice: true })

      expect(request.get).toHaveBeenCalledWith(
        '/colonel/activities/ACT-1/products/sync-jobs/JOB-1',
        { timeout: 15000, suppressErrorNotice: true }
      )
    })
  })
})
