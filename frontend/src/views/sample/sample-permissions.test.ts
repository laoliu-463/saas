import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../../constants/rbac'
import {
  canExportSamplesByRole,
  filterSampleTabsForOps,
  OPS_HIDDEN_SAMPLE_STATUSES,
  OPS_SHIPPING_TABS
} from './sample-permissions'

describe('sample permissions', () => {
  it('allows admins, biz roles, ops staff, and channel leaders to export samples', () => {
    expect(canExportSamplesByRole([ROLE_CODES.ADMIN])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.BIZ_LEADER])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.OPS_STAFF])).toBe(true)
    expect(canExportSamplesByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(true)
  })

  it('keeps channel staff from exporting samples', () => {
    expect(canExportSamplesByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(false)
  })

  it('hides audit-stage tabs from ops shipping views', () => {
    expect(OPS_HIDDEN_SAMPLE_STATUSES.has('PENDING_AUDIT')).toBe(true)
    expect(OPS_HIDDEN_SAMPLE_STATUSES.has('REJECTED')).toBe(true)
    expect(OPS_SHIPPING_TABS.map((tab) => tab.value)).not.toContain('REJECTED')
    expect(OPS_SHIPPING_TABS.map((tab) => tab.value)).not.toContain('PENDING_AUDIT')

    const filtered = filterSampleTabsForOps([
      { label: '待审核', value: 'PENDING_AUDIT' },
      { label: '待发货', value: 'PENDING_SHIP' },
      { label: '已拒绝', value: 'REJECTED' }
    ])
    expect(filtered).toEqual([{ label: '待发货', value: 'PENDING_SHIP' }])
  })
})
