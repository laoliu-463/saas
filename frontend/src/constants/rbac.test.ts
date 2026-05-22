import { describe, expect, it } from 'vitest'

import { ROLE_CODES, hasAccess, isAdminRole } from './rbac'

describe('rbac helpers', () => {
  it('allows routes without required roles', () => {
    expect(hasAccess()).toBe(true)
    expect(hasAccess([ROLE_CODES.BIZ_STAFF], [])).toBe(true)
  })

  it('allows admin and matching roles while rejecting unrelated roles', () => {
    expect(isAdminRole([ROLE_CODES.ADMIN])).toBe(true)
    expect(hasAccess([ROLE_CODES.ADMIN], [ROLE_CODES.BIZ_LEADER])).toBe(true)
    expect(hasAccess([ROLE_CODES.BIZ_STAFF], [ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(hasAccess([ROLE_CODES.CHANNEL_STAFF], [ROLE_CODES.BIZ_STAFF])).toBe(false)
  })
})
