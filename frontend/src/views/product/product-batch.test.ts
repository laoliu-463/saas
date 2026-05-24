import { describe, expect, it } from 'vitest'

import {
  formatBatchResultMessage,
  MAX_BATCH_PRODUCT_IDS,
  normalizeBatchProductIds
} from './product-batch'

describe('product-batch helpers', () => {
  it('deduplicates and caps batch product ids', () => {
    const ids = Array.from({ length: MAX_BATCH_PRODUCT_IDS + 5 }, (_, index) => String(index % 3))

    expect(normalizeBatchProductIds(ids)).toEqual(['0', '1', '2'])
    expect(normalizeBatchProductIds(['9001', '9001', '9002', ''])).toEqual(['9001', '9002'])
  })

  it('formats batch result messages', () => {
    expect(formatBatchResultMessage({ total: 3, succeeded: 3, failed: 0 }, '批量置顶')).toBe('批量置顶完成：3/3 成功')
    expect(formatBatchResultMessage({
      total: 2,
      succeeded: 1,
      failed: 1,
      failures: [{ productId: '9002', reason: '状态不允许' }]
    }, '批量加入商品库')).toBe('批量加入商品库完成：成功 1，失败 1（示例：状态不允许）')
  })
})
