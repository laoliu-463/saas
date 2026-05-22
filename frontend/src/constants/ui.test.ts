import { describe, expect, it } from 'vitest'

import { DRAWER_WIDTH, DRAWER_WIDTH_PX, MODAL_WIDTH } from './ui'

describe('ui constants', () => {
  it('exposes modal width tokens from small to 2xl', () => {
    expect(MODAL_WIDTH).toEqual({
      sm: 'var(--modal-width-sm)',
      md: 'var(--modal-width-md)',
      lg: 'var(--modal-width-lg)',
      xl: 'var(--modal-width-xl)',
      xxl: 'var(--modal-width-2xl)'
    })
  })

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
