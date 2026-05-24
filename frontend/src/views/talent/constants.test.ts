import { describe, expect, it } from 'vitest'
import { ROLE_CODES } from '../../constants/rbac'
import { getAccessibleTalentViewOptions } from './constants'

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

  it('returns all talent views for admin', () => {
    const views = getAccessibleTalentViewOptions([ROLE_CODES.ADMIN], true)
    expect(views.map((item) => item.value)).toContain('NATURAL_ORDERS')
    expect(views.map((item) => item.value)).toContain('BLACKLIST')
  })

  it('limits channel staff to public and private pools', () => {
    const views = getAccessibleTalentViewOptions([ROLE_CODES.CHANNEL_STAFF])
    expect(views.map((item) => item.value)).toEqual(['TEAM_PUBLIC', 'MY_TALENTS'])
  })
})
