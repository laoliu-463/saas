import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../../constants/rbac'
import { canExportSamplesByRole } from './sample-permissions'

describe('sample permissions', () => {
  it('allows admins, biz roles, and ops staff to export samples', () => {
    expect(canExportSamplesByRole([ROLE_CODES.ADMIN])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.BIZ_LEADER])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.OPS_STAFF])).toBe(true)
  })

  it('keeps channel roles from exporting samples', () => {
    expect(canExportSamplesByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(false)
    expect(canExportSamplesByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(false)
  })
})
