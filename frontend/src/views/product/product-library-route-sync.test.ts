import { describe, expect, it } from 'vitest'

import {
  buildQueryWithoutActivityId,
  isSameActivityId,
  resolveActivityIdFromQuery
} from './product-library-route-sync'

describe('product-library-route-sync (ADR-003)', () => {
  describe('resolveActivityIdFromQuery', () => {
    it('空对象返回空字符串', () => {
      expect(resolveActivityIdFromQuery({})).toBe('')
    })

    it('普通字符串正常读取并 trim', () => {
      expect(resolveActivityIdFromQuery({ activityId: 'act-1001' })).toBe('act-1001')
      expect(resolveActivityIdFromQuery({ activityId: '  act-1001  ' })).toBe('act-1001')
    })

    it('数组取首项非空', () => {
      expect(resolveActivityIdFromQuery({ activityId: ['act-1', 'act-2'] })).toBe('act-1')
      expect(resolveActivityIdFromQuery({ activityId: [null, 'act-2'] })).toBe('act-2')
    })

    it('null / undefined 视为空', () => {
      expect(resolveActivityIdFromQuery({ activityId: null })).toBe('')
      expect(resolveActivityIdFromQuery({ activityId: undefined })).toBe('')
    })
  })

  describe('buildQueryWithoutActivityId', () => {
    it('只删 activityId，其它键原样保留', () => {
      expect(
        buildQueryWithoutActivityId({
          activityId: 'act-1',
          sortBy: 'default',
          page: '2'
        })
      ).toEqual({ sortBy: 'default', page: '2' })
    })

    it('数组值取首项', () => {
      expect(
        buildQueryWithoutActivityId({
          activityId: 'act-1',
          keyword: ['kw-1', 'kw-2']
        })
      ).toEqual({ keyword: 'kw-1' })
    })

    it('空值 / null / 空字符串不写入新 query', () => {
      expect(
        buildQueryWithoutActivityId({
          activityId: 'act-1',
          a: '',
          b: null,
          c: 'keep'
        })
      ).toEqual({ c: 'keep' })
    })

    it('query 本来就只有 activityId 时返回空对象', () => {
      expect(buildQueryWithoutActivityId({ activityId: 'act-1' })).toEqual({})
    })
  })

  describe('isSameActivityId', () => {
    it('完全相同 → true', () => {
      expect(isSameActivityId('act-1', 'act-1')).toBe(true)
    })

    it('含空白 → 视为相等', () => {
      expect(isSameActivityId('act-1', '  act-1  ')).toBe(true)
    })

    it('null 与空字符串视为相等', () => {
      expect(isSameActivityId(null, '')).toBe(true)
      expect(isSameActivityId(undefined, null)).toBe(true)
    })

    it('不同值 → false', () => {
      expect(isSameActivityId('act-1', 'act-2')).toBe(false)
    })
  })
})
