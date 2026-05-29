import { describe, expect, it } from 'vitest'

import {
  buildOperationSummary,
  formatOperationTime,
  formatOperatorLabel,
  formatStatusFlow,
  getBizStatusLabel,
  getDecisionLevelLabel,
  getOperationTypeLabel,
  isUuidLike,
  mapProductOperationLogView,
  mapProductOperationLogViews,
  normalizeLogText,
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

  it('normalizeLogText handles edge cases', () => {
    expect(normalizeLogText(null)).toBe('')
    expect(normalizeLogText(undefined)).toBe('')
    expect(normalizeLogText('null')).toBe('')
    expect(normalizeLogText('  trimmed  ')).toBe('trimmed')
  })

  it('isUuidLike detects UUID format', () => {
    expect(isUuidLike('550e8400-e29b-41d4-a716-446655440000')).toBe(true)
    expect(isUuidLike('not-a-uuid')).toBe(false)
    expect(isUuidLike(null)).toBe(false)
  })

  it('getBizStatusLabel maps known and unknown statuses', () => {
    expect(getBizStatusLabel('PENDING_AUDIT')).toBe('待审核')
    expect(getBizStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(getBizStatusLabel(null)).toBe('未知状态')
  })

  it('getOperationTypeLabel maps BIND_ACTIVITY', () => {
    expect(getOperationTypeLabel('BIND_ACTIVITY')).toBe('绑定活动')
    expect(getOperationTypeLabel('LINK')).toBe('生成推广链接')
    expect(getOperationTypeLabel('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE')
  })

  it('getDecisionLevelLabel maps decision levels', () => {
    expect(getDecisionLevelLabel('MAIN')).toBe('主推')
    expect(getDecisionLevelLabel('DROP')).toBe('放弃')
    expect(getDecisionLevelLabel(null)).toBe('暂无判断')
  })

  it('formatOperationTime formats ISO date strings', () => {
    expect(formatOperationTime('2026-05-28T10:30:00')).toContain('2026-05-28')
    expect(formatOperationTime(null)).toBe('—')
    expect(formatOperationTime('invalid')).toBe('invalid')
  })

  it('formatOperatorLabel returns operator name from payload', () => {
    const row = {
      operationType: 'ASSIGN',
      operatorId: 'user-123',
      operationPayload: '{operatorName=张三}'
    }
    expect(formatOperatorLabel(row)).toBe('张三')
  })

  it('formatOperatorLabel returns system for SYNC operations', () => {
    expect(formatOperatorLabel({ operationType: 'SYNC' })).toBe('系统同步')
  })

  it('formatOperatorLabel returns historical label for UUID operators', () => {
    expect(formatOperatorLabel({ operatorId: '550e8400-e29b-41d4-a716-446655440000' })).toBe('历史操作人')
  })

  it('formatOperatorLabel returns operatorId as fallback', () => {
    expect(formatOperatorLabel({ operatorId: 'user-456' })).toBe('user-456')
  })

  it('buildOperationSummary handles SYNC operation', () => {
    const summary = buildOperationSummary({ operationType: 'SYNC' })
    expect(summary).toBe('活动商品已同步到本地商品池')
  })

  it('buildOperationSummary handles ASSIGN_AUDIT operation', () => {
    const summary = buildOperationSummary({
      operationType: 'ASSIGN_AUDIT',
      operationPayload: '{assigneeName=李四}'
    })
    expect(summary).toContain('李四')
  })

  it('buildOperationSummary handles AUDIT operation with REJECTED', () => {
    const summary = buildOperationSummary({
      operationType: 'AUDIT',
      afterStatus: 'REJECTED'
    })
    expect(summary).toContain('审核未通过')
  })

  it('buildOperationSummary handles AUDIT operation with APPROVED', () => {
    const summary = buildOperationSummary({
      operationType: 'AUDIT',
      afterStatus: 'APPROVED'
    })
    expect(summary).toContain('审核通过')
  })

  it('buildOperationSummary handles TALENT_FOLLOW operation', () => {
    const summary = buildOperationSummary({ operationType: 'TALENT_FOLLOW' })
    expect(summary).toBe('商品已进入达人跟进流程')
  })

  it('buildOperationSummary handles TALENT_FOLLOW_APPEND operation', () => {
    const summary = buildOperationSummary({ operationType: 'TALENT_FOLLOW_APPEND' })
    expect(summary).toBe('已追加达人跟进记录')
  })

  it('buildOperationSummary handles default case', () => {
    const summary = buildOperationSummary({ operationType: 'CUSTOM_ACTION' })
    expect(summary).toBe('完成一次业务操作')
  })

  it('buildOperationSummary returns eventLabel in default case when available', () => {
    const summary = buildOperationSummary({
      operationType: 'CUSTOM_ACTION',
      operationPayload: '{eventLabel=自定义操作}'
    })
    expect(summary).toBe('自定义操作')
  })

  it('mapProductOperationLogView includes detailLines for ASSIGN operation', () => {
    const view = mapProductOperationLogView({
      operationType: 'ASSIGN',
      operationPayload: '{assigneeName=王五}'
    })
    expect(view.detailLines).toContain('招商组长：王五')
  })

  it('mapProductOperationLogView includes detailLines for BIND_ACTIVITY operation', () => {
    const view = mapProductOperationLogView({
      operationType: 'BIND_ACTIVITY',
      operationPayload: '{boundActivityId=ACT-123}'
    })
    expect(view.detailLines).toContain('绑定活动：ACT-123')
  })

  it('mapProductOperationLogViews maps multiple rows', () => {
    const rows = [
      { id: '1', operationType: 'SYNC', operationPayload: '{}' },
      { id: '2', operationType: 'LIBRARY_ENTRY', operationPayload: '{}' }
    ]
    const views = mapProductOperationLogViews(rows)
    expect(views.length).toBe(2)
    expect(views[0].eventLabel).toBe('同步商品')
    expect(views[1].eventLabel).toBe('加入商品库')
  })

  it('mapProductOperationLogView handles failed operation', () => {
    const view = mapProductOperationLogView({
      id: 'log-1',
      operationType: 'LIBRARY_ENTRY',
      success: false,
      errorMessage: '网络错误'
    })
    expect(view.success).toBe(false)
    expect(view.eventTagType).toBe('error')
    expect(view.failureReason).toBe('网络错误')
  })

  it('mapProductOperationLogView uses index for id when id is missing', () => {
    const view = mapProductOperationLogView({ operationType: 'SYNC' }, 5)
    expect(view.id).toBe('SYNC-5')
  })
})
