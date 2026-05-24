import { describe, expect, it } from 'vitest'
import {
  ACTIVITY_STATUS_TABS,
  buildBuyinActivityUrl,
  countActivityProductStats,
  formatActivityCategories,
  formatMechanismSummary,
  resolveActivityStatusLabel
} from './activity-list-display'

describe('activity-list-display', () => {
  it('builds buyin activity url', () => {
    expect(buildBuyinActivityUrl('3920684')).toContain('3920684')
  })

  it('formats categories from map and array', () => {
    expect(formatActivityCategories({ 一级类目: '美妆个护' })).toBe('美妆个护')
    expect(formatActivityCategories(['玩具乐器', '服饰内衣'])).toBe('玩具乐器、服饰内衣')
  })

  it('resolves status label from row', () => {
    expect(resolveActivityStatusLabel({ statusText: '报名中' })).toBe('报名中')
    expect(resolveActivityStatusLabel({ status: 5 })).toBe('推广中')
  })

  it('counts promoting and pending products', () => {
    expect(
      countActivityProductStats([
        { status: 1, statusText: '推广中' },
        { status: 0, statusText: '待审核' },
        { status: 3, statusText: '合作已终止' }
      ])
    ).toEqual({ promoting: 1, pending: 1 })
  })

  it('formats mechanism summary with defaults', () => {
    expect(formatMechanismSummary({})).toMatchObject({
      typeLabel: '无限制',
      commissionLine: expect.stringContaining('日常≥0.00%'),
      serviceLine: expect.stringContaining('日常≥0.00%')
    })
  })

  it('exposes seven status tabs including all', () => {
    expect(ACTIVITY_STATUS_TABS).toHaveLength(7)
    expect(ACTIVITY_STATUS_TABS[0]?.label).toBe('全部')
  })
})
