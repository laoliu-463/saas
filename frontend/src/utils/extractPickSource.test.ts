import { describe, expect, it } from 'vitest'

import { extractPickSourceFromUrl } from './extractPickSource'

describe('extractPickSourceFromUrl', () => {
  it('reads pick_source from a valid promotion url', () => {
    expect(extractPickSourceFromUrl('https://buyin.example/link?foo=1&pick_source=ps_123')).toBe('ps_123')
  })

  it('returns null when the url has no pick_source query', () => {
    expect(extractPickSourceFromUrl('https://buyin.example/link?foo=1')).toBeNull()
  })

  it('returns null for blank or invalid urls', () => {
    expect(extractPickSourceFromUrl('')).toBeNull()
    expect(extractPickSourceFromUrl(null)).toBeNull()
    expect(extractPickSourceFromUrl('not a url')).toBeNull()
  })
})
