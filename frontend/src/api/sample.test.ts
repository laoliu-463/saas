import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  batchApproveSamples,
  batchRejectSamples,
  batchShipSamples,
  getSampleEditContext,
  getSampleOrderCopy,
  getSamplePrivateNote,
  getSamplePromotionCopy,
  downloadLogisticsImportTemplate,
  getSampleLogistics,
  getSampleFilterOptions,
  getSampleStatusTransitions,
  importSampleLogistics,
  saveSamplePrivateNote,
  updateSampleCooperationDetails,
  syncAllSampleLogistics,
  syncSampleLogistics
} from './sample'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

describe('sample API', () => {
  it('calls status transitions endpoint', () => {
    getSampleStatusTransitions()

    expect(request.get).toHaveBeenCalledWith('/samples/status-transitions')
  })

  it('calls filter options endpoint', () => {
    getSampleFilterOptions()

    expect(request.get).toHaveBeenCalledWith('/samples/filter-options')
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

  it('calls sample logistics endpoints', () => {
    syncSampleLogistics('sample-1')
    getSampleLogistics('sample-1')
    syncAllSampleLogistics()
    downloadLogisticsImportTemplate()

    expect(request.post).toHaveBeenCalledWith('/samples/sample-1/logistics/sync')
    expect(request.get).toHaveBeenCalledWith('/samples/sample-1/logistics')
    expect(request.post).toHaveBeenCalledWith('/admin/samples/logistics/sync')
    expect(request.get).toHaveBeenCalledWith('/samples/logistics/import-template', { responseType: 'blob' })
  })

  it('uploads logistics import form data without overwriting by default', () => {
    const file = new File(['x'], 'logistics.xlsx')

    importSampleLogistics(file)

    const [url, body, config] = vi.mocked(request.post).mock.calls.at(-1) || []
    expect(url).toBe('/samples/logistics/import?allowOverwrite=false')
    expect(body).toBeInstanceOf(FormData)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })

  it('calls cooperation edit, copy and private note endpoints', () => {
    getSampleEditContext('sample-1')
    updateSampleCooperationDetails('sample-1', {
      version: 3,
      remark: '请尽快寄出',
      recipientName: '薄荷',
      recipientPhone: '15093177715',
      recipientAddress: '河南省郑州市巩义市回郭镇东庙村'
    })
    getSamplePromotionCopy('sample-1')
    getSampleOrderCopy('sample-1')
    getSamplePrivateNote('sample-1')
    saveSamplePrivateNote('sample-1', { content: '仅自己可见' })

    expect(request.get).toHaveBeenCalledWith('/samples/sample-1/edit-context')
    expect(request.put).toHaveBeenCalledWith('/samples/sample-1/cooperation-details', {
      version: 3,
      remark: '请尽快寄出',
      recipientName: '薄荷',
      recipientPhone: '15093177715',
      recipientAddress: '河南省郑州市巩义市回郭镇东庙村'
    })
    expect(request.post).toHaveBeenCalledWith('/samples/sample-1/promotion-copy')
    expect(request.get).toHaveBeenCalledWith('/samples/sample-1/order-copy')
    expect(request.get).toHaveBeenCalledWith('/samples/sample-1/private-note')
    expect(request.put).toHaveBeenCalledWith('/samples/sample-1/private-note', { content: '仅自己可见' })
  })
})
