import { describe, expect, it } from 'vitest'
import { ROLE_CODES } from '../../constants/rbac'
import {
  canApplySampleFromTalentByRole,
  canManageTalentBlacklistByRole,
  canRefreshWeeklyTalentByRole,
  getAccessibleTalentViewOptions,
  isChannelStaffOnlyTalentRole
} from './constants'

describe('getAccessibleTalentViewOptions', () => {
  it('returns all views for channel leader', () => {
    const views = getAccessibleTalentViewOptions([ROLE_CODES.CHANNEL_LEADER])
    expect(views.map((item) => item.value)).toEqual([
      'TEAM_PUBLIC',
      'MY_TALENTS',
      'TEAM_PRIVATE',
      'NATURAL_ORDERS',
      'BLACKLIST'
    ])
  })

  it('keeps legacy channel leader cache compatible', () => {
    const views = getAccessibleTalentViewOptions(['qd_leader'])
    expect(views.map((item) => item.value)).toContain('TEAM_PRIVATE')
    expect(views.map((item) => item.value)).toContain('BLACKLIST')
  })

  it('returns all talent views for admin', () => {
    const views = getAccessibleTalentViewOptions([ROLE_CODES.ADMIN], true)
    expect(views.map((item) => item.value)).toContain('NATURAL_ORDERS')
    expect(views.map((item) => item.value)).toContain('BLACKLIST')
  })

  it('limits channel staff to public and private pools', () => {
    const views = getAccessibleTalentViewOptions([ROLE_CODES.CHANNEL_STAFF])
    expect(views.map((item) => item.value)).toEqual(['TEAM_PUBLIC', 'MY_TALENTS'])
  })

  it('centralizes talent page role gates', () => {
    expect(isChannelStaffOnlyTalentRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
    expect(isChannelStaffOnlyTalentRole([ROLE_CODES.CHANNEL_STAFF, ROLE_CODES.CHANNEL_LEADER])).toBe(false)
    expect(canRefreshWeeklyTalentByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(true)
    expect(canRefreshWeeklyTalentByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(false)
    expect(canManageTalentBlacklistByRole([ROLE_CODES.CHANNEL_LEADER])).toBe(true)
    expect(canManageTalentBlacklistByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(false)
    expect(canManageTalentBlacklistByRole([], true)).toBe(true)
    expect(canApplySampleFromTalentByRole([ROLE_CODES.CHANNEL_STAFF])).toBe(true)
    expect(canApplySampleFromTalentByRole([ROLE_CODES.BIZ_STAFF])).toBe(false)
    expect(canApplySampleFromTalentByRole([], true)).toBe(true)
  })
})
