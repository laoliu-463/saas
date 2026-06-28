import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../../constants/rbac'
import {
  ALL_SAMPLE_STATUS_TABS,
  buildSampleFlowSteps,
  canApplySampleByRole,
  canAuditSamplesByRole,
  canExportSamplesByRole,
  canShipSamplesByRole,
  canSubmitSampleByRole,
  channelStaffWaitingHint,
  channelStaffWaitingTagType,
  filterSampleTabsForOps,
  isBizStaffOnlyRole,
  isChannelStaffOnlyRole,
  isOpsStaffOnlyRole,
  OPS_HIDDEN_SAMPLE_STATUSES,
  OPS_SHIPPING_TABS,
  SAMPLE_STATUS,
  sampleStatusLabel,
  sampleStatusTagType
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

  it('normalizes legacy cached roles before checking export visibility', () => {
    expect(canExportSamplesByRole(['zs_leader'])).toBe(true)
    expect(canExportSamplesByRole(['qd_staff'])).toBe(false)
  })

  it('hides audit-stage tabs from ops shipping views', () => {
    expect(OPS_HIDDEN_SAMPLE_STATUSES.has(SAMPLE_STATUS.PENDING_AUDIT)).toBe(true)
    expect(OPS_HIDDEN_SAMPLE_STATUSES.has(SAMPLE_STATUS.REJECTED)).toBe(true)
    expect(OPS_SHIPPING_TABS.map((tab) => tab.value)).not.toContain(SAMPLE_STATUS.REJECTED)
    expect(OPS_SHIPPING_TABS.map((tab) => tab.value)).not.toContain(SAMPLE_STATUS.PENDING_AUDIT)

    const filtered = filterSampleTabsForOps([
      { label: '待审核', value: SAMPLE_STATUS.PENDING_AUDIT },
      { label: '待发货', value: SAMPLE_STATUS.PENDING_SHIP },
      { label: '已拒绝', value: SAMPLE_STATUS.REJECTED }
    ])
    expect(filtered).toEqual([{ label: '待发货', value: SAMPLE_STATUS.PENDING_SHIP }])
  })

  it('keeps sample status tabs and display labels centralized', () => {
    expect(ALL_SAMPLE_STATUS_TABS.map((tab) => tab.value)).toEqual([
      '',
      SAMPLE_STATUS.PENDING_AUDIT,
      SAMPLE_STATUS.PENDING_SHIP,
      SAMPLE_STATUS.SHIPPED,
      SAMPLE_STATUS.PENDING_TASK,
      SAMPLE_STATUS.FINISHED,
      SAMPLE_STATUS.REJECTED,
      SAMPLE_STATUS.CLOSED
    ])
    expect(sampleStatusLabel(SAMPLE_STATUS.PENDING_TASK)).toBe('待交作业')
    expect(sampleStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(sampleStatusLabel()).toBe('-')
  })

  it('maps sample status tags for UI display only', () => {
    expect(sampleStatusTagType(SAMPLE_STATUS.FINISHED)).toBe('success')
    expect(sampleStatusTagType(SAMPLE_STATUS.REJECTED)).toBe('error')
    expect(sampleStatusTagType(SAMPLE_STATUS.SHIPPED)).toBe('info')
    expect(sampleStatusTagType(SAMPLE_STATUS.PENDING_SHIP)).toBe('warning')
  })

  it('centralizes sample role gates used by pages', () => {
    expect(canApplySampleByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(true)
    expect(canApplySampleByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
    expect(canApplySampleByRole([ROLE_CODES.BIZ_STAFF])).toBe(false)
    expect(canAuditSamplesByRole([ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(canAuditSamplesByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(false)
    expect(canAuditSamplesByRole([], true)).toBe(true)
    expect(canShipSamplesByRole([ROLE_CODES.OPS_STAFF])).toBe(true)
    expect(canShipSamplesByRole([ROLE_CODES.BIZ_STAFF])).toBe(false)
    expect(canSubmitSampleByRole(['VISITOR'])).toBe(false)
    expect(canSubmitSampleByRole(['READONLY'])).toBe(false)
    expect(canSubmitSampleByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
  })

  it('detects role-only page modes without replacing backend authorization', () => {
    expect(isOpsStaffOnlyRole([ROLE_CODES.OPS_STAFF])).toBe(true)
    expect(isOpsStaffOnlyRole([ROLE_CODES.OPS_STAFF], true)).toBe(false)
    expect(isBizStaffOnlyRole([ROLE_CODES.BIZ_STAFF])).toBe(true)
    expect(isBizStaffOnlyRole([ROLE_CODES.BIZ_STAFF, ROLE_CODES.BIZ_LEADER])).toBe(false)
    expect(isChannelStaffOnlyRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
    expect(isChannelStaffOnlyRole([ROLE_CODES.CHANNEL_STAFF, ROLE_CODES.CHANNEL_LEADER])).toBe(false)
  })

  it('builds sample flow steps from the backend status value', () => {
    expect(buildSampleFlowSteps(SAMPLE_STATUS.PENDING_SHIP).map((step) => step.state)).toEqual([
      'done',
      'active',
      'pending',
      'pending',
      'pending'
    ])
    expect(buildSampleFlowSteps(SAMPLE_STATUS.REJECTED).every((step) => step.state === 'muted')).toBe(true)
    expect(channelStaffWaitingHint(SAMPLE_STATUS.PENDING_AUDIT)).toBe('等待招商审核')
    expect(channelStaffWaitingTagType(SAMPLE_STATUS.PENDING_SHIP)).toBe('warning')
    expect(channelStaffWaitingTagType(SAMPLE_STATUS.SHIPPED)).toBe('info')
    expect(channelStaffWaitingHint(SAMPLE_STATUS.FINISHED)).toBe('')
  })
})
