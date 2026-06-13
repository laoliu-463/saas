import { describe, expect, it } from 'vitest'

import {
  getOrderDetailSectionSource,
  ORDER_DETAIL_SECTION_SOURCES
} from './orderDetailFieldSources'

describe('orderDetailFieldSources', () => {
  it('should expose all order detail sections', () => {
    expect(Object.keys(ORDER_DETAIL_SECTION_SOURCES)).toEqual([
      'basic',
      'amount',
      'attribution',
      'promotion',
      'product',
      'talent',
      'sample'
    ])
  })

  it('should return stable source text for attribution section', () => {
    expect(getOrderDetailSectionSource('attribution')).toContain('attribution_status')
  })
})
