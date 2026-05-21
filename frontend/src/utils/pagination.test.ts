import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PAGE_SIZE,
  MAX_PAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  createPaginationState,
  normalizePage,
  normalizePageSize
} from './pagination'

describe('pagination helpers', () => {
  it('uses the project default page size and exposes the maximum selectable size', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(20)
    expect(MAX_PAGE_SIZE).toBe(100)
    expect(PAGE_SIZE_OPTIONS).toEqual([20, 50, 100])
    expect(createPaginationState()).toMatchObject({
      page: 1,
      pageSize: 20,
      itemCount: 0,
      showSizePicker: true,
      pageSizes: [20, 50, 100]
    })
  })

  it('normalizes invalid page numbers and caps page size at 100', () => {
    expect(normalizePage(0)).toBe(1)
    expect(normalizePage('abc')).toBe(1)
    expect(normalizePage(3.8)).toBe(3)
    expect(normalizePageSize(0)).toBe(20)
    expect(normalizePageSize('abc')).toBe(20)
    expect(normalizePageSize(500)).toBe(100)
  })
})
