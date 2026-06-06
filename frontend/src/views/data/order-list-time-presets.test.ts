import { describe, expect, it } from 'vitest'

import {
  buildLastNDaysRange,
  buildRecentRange,
  buildTodayRange,
  buildYesterdayRange,
  getRecentPresetLabel
} from './order-list-time-presets'

const baseDate = new Date(2026, 5, 6, 15, 30, 0)

describe('order-list-time-presets', () => {
  it('builds today range', () => {
    const [start, end] = buildTodayRange(baseDate)
    expect(new Date(start).getFullYear()).toBe(2026)
    expect(new Date(start).getMonth()).toBe(5)
    expect(new Date(start).getDate()).toBe(6)
    expect(new Date(start).getHours()).toBe(0)
    expect(new Date(end).getHours()).toBe(23)
    expect(new Date(end).getMinutes()).toBe(59)
  })

  it('builds yesterday range', () => {
    const [start, end] = buildYesterdayRange(baseDate)
    expect(new Date(start).getDate()).toBe(5)
    expect(new Date(end).getDate()).toBe(5)
  })

  it('builds last 15 days including today', () => {
    const [start, end] = buildLastNDaysRange(15, baseDate)
    expect(new Date(start).getDate()).toBe(23)
    expect(new Date(start).getMonth()).toBe(4)
    expect(new Date(end).getDate()).toBe(6)
  })

  it('builds recent ranges for popup options', () => {
    expect(buildRecentRange('today', baseDate)).toEqual(buildTodayRange(baseDate))
    expect(buildRecentRange('yesterday', baseDate)).toEqual(buildYesterdayRange(baseDate))
    expect(buildRecentRange('15d', baseDate)).toEqual(buildLastNDaysRange(15, baseDate))
    expect(buildRecentRange('30d', baseDate)).toEqual(buildLastNDaysRange(30, baseDate))
  })

  it('shows selected recent label on trigger button', () => {
    expect(getRecentPresetLabel('week', '15d')).toBe('近N天')
    expect(getRecentPresetLabel('recent', 'today')).toBe('今天')
    expect(getRecentPresetLabel('recent', '30d')).toBe('30天')
  })
})
