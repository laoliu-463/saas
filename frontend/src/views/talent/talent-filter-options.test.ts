import { describe, expect, it } from 'vitest'
import { resolveFansBandRange } from './talent-filter-options'

describe('resolveFansBandRange', () => {
  it('maps fan bands to min/max fans', () => {
    expect(resolveFansBandRange('1W~5W')).toEqual({ minFans: 10000, maxFans: 49999 })
    expect(resolveFansBandRange('100W以上')).toEqual({ minFans: 1000000 })
    expect(resolveFansBandRange(null)).toEqual({})
  })
})
