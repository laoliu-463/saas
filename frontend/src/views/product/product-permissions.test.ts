import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../../constants/rbac'
import { canApplyQuickSampleByRole } from './product-permissions'

describe('product permissions', () => {
  it('allows business and channel roles to start quick sample', () => {
    expect(canApplyQuickSampleByRole([ROLE_CODES.BIZ_LEADER])).toBe(true)
    expect(canApplyQuickSampleByRole([ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(canApplyQuickSampleByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(true)
    expect(canApplyQuickSampleByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
  })

  it('keeps logistics-only roles out of quick sample', () => {
    expect(canApplyQuickSampleByRole([ROLE_CODES.OPS_STAFF])).toBe(false)
  })

  it('allows administrators through the global bypass', () => {
    expect(canApplyQuickSampleByRole([], true)).toBe(true)
  })
})
