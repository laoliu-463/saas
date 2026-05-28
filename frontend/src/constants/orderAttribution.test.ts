/**
 * 订单归因常量模块单元测试
 *
 * 测试覆盖范围：
 * - getAttributionReasonText：原生团长映射原因、达人认领冲突原因的本地化
 * - getAttributionReasonSuggestion：原因对应操作建议的正确返回
 * - 边界情况：空值、未知原因码的回退行为
 * - getAttributionStatusText：归因状态文案的正确映射和回退
 */
import { describe, expect, it } from 'vitest'

import {
  getAttributionReasonSuggestion,
  getAttributionReasonText,
  getAttributionStatusText
} from './orderAttribution'

describe('orderAttribution', () => {
  // 验证：原生团长映射相关原因码能正确本地化为中文文案，且建议包含相关关键词
  it('localizes native colonel mapping reasons', () => {
    expect(getAttributionReasonText('COLONEL_MAPPING_NOT_FOUND')).toBe('原生团长订单未找到归因映射')
    expect(getAttributionReasonText('COLONEL_MAPPING_AMBIGUOUS')).toBe('原生团长订单命中多条归因映射')
    expect(getAttributionReasonSuggestion('COLONEL_MAPPING_NOT_FOUND')).toContain('原生团长字段')
    expect(getAttributionReasonSuggestion('COLONEL_MAPPING_AMBIGUOUS')).toContain('重复的原生团长映射')
  })

  // 验证：达人认领冲突原因的本地化和建议文案
  it('localizes talent claim owner conflict reason', () => {
    expect(getAttributionReasonText('TALENT_CLAIM_OWNER_CONFLICT')).toBe('归因负责人和达人认领人不一致')
    expect(getAttributionReasonSuggestion('TALENT_CLAIM_OWNER_CONFLICT')).toContain('有效认领记录')
  })

  // 验证：空值和未知原因码的回退行为
  it('falls back for blank and unknown attribution reasons', () => {
    // null 返回占位符 '-'
    expect(getAttributionReasonText(null)).toBe('-')
    // 未知原因码原样返回
    expect(getAttributionReasonText('CUSTOM_REASON')).toBe('CUSTOM_REASON')
    // null/undefined 返回通用建议
    expect(getAttributionReasonSuggestion(undefined)).toBe('请检查商品转链、达人渠道和订单回流情况。')
    // 未知原因码也返回通用建议
    expect(getAttributionReasonSuggestion('CUSTOM_REASON')).toBe('请检查商品转链、达人渠道和订单回流情况。')
  })

  // 验证：归因状态文案的正确映射和未知状态码的回退
  it('formats attribution statuses with fallbacks', () => {
    expect(getAttributionStatusText(null)).toBe('-')
    expect(getAttributionStatusText('ATTRIBUTED')).toBe('已确认业绩')
    // 未知状态码原样返回
    expect(getAttributionStatusText('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
  })
})
