import { describe, expect, it } from 'vitest'

import {
  buildOperationSummary,
  formatStatusFlow,
  getOperationTypeLabel,
  mapProductOperationLogView,
  parseOperationPayload
} from './product-operation-log-display'

describe('product-operation-log-display', () => {
  it('parses Java map payload and formats library entry summary', () => {
    const payload = parseOperationPayload('{eventLabel=加入商品库, productTitle=主演示商品-高佣款-1}')
    expect(payload.eventLabel).toBe('加入商品库')
    expect(payload.productTitle).toBe('主演示商品-高佣款-1')

    const summary = buildOperationSummary({
      operationType: 'LIBRARY_ENTRY',
      operationPayload: '{eventLabel=加入商品库, productTitle=主演示商品-高佣款-1}'
    })
    expect(summary).toContain('主演示商品-高佣款-1')
    expect(getOperationTypeLabel('LIBRARY_ENTRY')).toBe('加入商品库')
  })

  it('hides unchanged status flow and maps decision remark', () => {
    expect(formatStatusFlow('APPROVED', 'APPROVED')).toBeNull()
    expect(formatStatusFlow('PENDING_AUDIT', 'APPROVED')).toBe('待审核 → 审核通过')

    const view = mapProductOperationLogView({
      operationType: 'DECISION',
      operationRemark: '佣金偏低，先暂缓',
      operationPayload: '{decisionLevel=PAUSE, decisionLabel=暂缓}',
      success: true
    })
    expect(view.eventLabel).toBe('推进判断')
    expect(view.summary).toBe('暂缓：佣金偏低，先暂缓')
    expect(view.eventTagType).toBe('warning')
  })
})
