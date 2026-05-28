/**
 * UI 布局常量模块单元测试
 *
 * 测试覆盖范围：
 * - MODAL_WIDTH：弹窗宽度 CSS 变量完整性验证
 * - DRAWER_WIDTH：抽屉宽度 CSS 变量和像素值的完整性验证
 */
import { describe, expect, it } from 'vitest'

import { DRAWER_WIDTH, DRAWER_WIDTH_PX, MODAL_WIDTH } from './ui'

describe('ui constants', () => {
  // 验证：弹窗宽度常量包含从 sm 到 2xl 的完整 CSS 变量映射
  it('exposes modal width tokens from small to 2xl', () => {
    expect(MODAL_WIDTH).toEqual({
      sm: 'var(--modal-width-sm)',
      md: 'var(--modal-width-md)',
      lg: 'var(--modal-width-lg)',
      xl: 'var(--modal-width-xl)',
      xxl: 'var(--modal-width-2xl)'
    })
  })

  // 验证：抽屉宽度常量同时包含 CSS 变量映射和像素值映射
  it('exposes drawer token widths and numeric px widths', () => {
    expect(DRAWER_WIDTH).toEqual({
      md: 'var(--drawer-width-md)',
      lg: 'var(--drawer-width-lg)'
    })
    expect(DRAWER_WIDTH_PX).toEqual({
      md: 640,
      lg: 860
    })
  })
})
