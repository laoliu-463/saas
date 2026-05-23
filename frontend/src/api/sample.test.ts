import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { batchApproveSamples, batchRejectSamples, batchShipSamples, getSampleStatusTransitions } from './sample'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('sample API', () => {
  it('calls status transitions endpoint', () => {
    getSampleStatusTransitions()

    expect(request.get).toHaveBeenCalledWith('/samples/status-transitions')
  })

  it('calls batch approve endpoint', () => {
    batchApproveSamples({ requestNos: ['SR-1', 'SR-2'], remark: '同意寄样' })

    expect(request.post).toHaveBeenCalledWith('/samples/batch-approve', {
      requestNos: ['SR-1', 'SR-2'],
      remark: '同意寄样'
    })
  })

  it('calls batch reject endpoint', () => {
    batchRejectSamples({ requestNos: ['SR-1'], remark: '库存不足' })

    expect(request.post).toHaveBeenCalledWith('/samples/batch-reject', {
      requestNos: ['SR-1'],
      remark: '库存不足'
    })
  })

  it('keeps batch ship endpoint unchanged', () => {
    batchShipSamples({ items: [{ requestNo: 'SR-1', trackingNo: 'SF123', shipperCode: 'SF' }] })

    expect(request.post).toHaveBeenCalledWith('/samples/batch-ship', {
      items: [{ requestNo: 'SR-1', trackingNo: 'SF123', shipperCode: 'SF' }]
    })
  })
})
