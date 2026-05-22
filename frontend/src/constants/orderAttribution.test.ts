import { describe, expect, it } from 'vitest'
import {
  getAttributionReasonSuggestion,
  getAttributionReasonText,
  getAttributionStatusText
} from './orderAttribution'

describe('orderAttribution', () => {
  it('localizes native colonel mapping reasons', () => {
    expect(getAttributionReasonText('COLONEL_MAPPING_NOT_FOUND')).toBe('原生团长订单未找到归因映射')
    expect(getAttributionReasonText('COLONEL_MAPPING_AMBIGUOUS')).toBe('原生团长订单命中多条归因映射')
    expect(getAttributionReasonSuggestion('COLONEL_MAPPING_NOT_FOUND')).toContain('原生团长字段')
    expect(getAttributionReasonSuggestion('COLONEL_MAPPING_AMBIGUOUS')).toContain('重复的原生团长映射')
  })

  it('localizes talent claim owner conflict reason', () => {
    expect(getAttributionReasonText('TALENT_CLAIM_OWNER_CONFLICT')).toBe('归因负责人和达人认领人不一致')
    expect(getAttributionReasonSuggestion('TALENT_CLAIM_OWNER_CONFLICT')).toContain('有效认领记录')
  })

  it('falls back for blank and unknown attribution reasons', () => {
    expect(getAttributionReasonText(null)).toBe('-')
    expect(getAttributionReasonText('CUSTOM_REASON')).toBe('CUSTOM_REASON')
    expect(getAttributionReasonSuggestion(undefined)).toBe('请检查商品转链、达人渠道和订单回流情况。')
    expect(getAttributionReasonSuggestion('CUSTOM_REASON')).toBe('请检查商品转链、达人渠道和订单回流情况。')
  })

  it('formats attribution statuses with fallbacks', () => {
    expect(getAttributionStatusText(null)).toBe('-')
    expect(getAttributionStatusText('ATTRIBUTED')).toBe('已确认业绩')
    expect(getAttributionStatusText('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
  })
})
