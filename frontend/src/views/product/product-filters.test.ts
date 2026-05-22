import { describe, expect, it } from 'vitest'

import { matchPromotionLink } from './product-filters'

describe('product filters', () => {
  it('treats ready or existing promotion links as linked', () => {
    expect(matchPromotionLink({ promotionLinkStatus: 'READY', promotionLink: 'https://v.douyin.com/ready/' }, 'LINKED')).toBe(true)
    expect(matchPromotionLink({ shortLink: 'https://v.douyin.com/short/' }, 'LINKED')).toBe(true)
    expect(matchPromotionLink({ promotion: { link: 'https://v.douyin.com/promo/' } }, 'LINKED')).toBe(true)
  })

  it('keeps explicit failed and pending promotion states distinct', () => {
    expect(matchPromotionLink({ promotionLinkStatus: 'FAILED' }, 'FAILED')).toBe(true)
    expect(matchPromotionLink({ promotionLinkStatus: 'FAILED' }, 'PENDING')).toBe(false)
    expect(matchPromotionLink({}, 'PENDING')).toBe(true)
  })
})
