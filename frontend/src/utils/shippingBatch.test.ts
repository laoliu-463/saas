import { describe, expect, it } from 'vitest'

import { parseBatchShipRows } from './shippingBatch'

describe('parseBatchShipRows', () => {
  it('requires the shipper code before creating a batch shipping item', () => {
    const result = parseBatchShipRows([
      ['寄样单号', '物流公司', '物流单号'],
      ['SR-1', '', 'SF123'],
      ['SR-2', 'SF', 'SF456']
    ])

    expect(result.items).toEqual([
      { requestNo: 'SR-2', shipperCode: 'SF', trackingNo: 'SF456' }
    ])
    expect(result.errors).toContain('第 2 行：物流公司编码为空，已跳过')
  })
})
