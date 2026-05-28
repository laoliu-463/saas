import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { convertActivityProductLink } from './activityProduct'

vi.mock('../utils/request', () => ({
  default: {
    post: vi.fn()
  }
}))

describe('activity product API', () => {
  it('passes optional request config when converting activity product links', () => {
    const config = { suppressErrorNotice: true }

    convertActivityProductLink('activity-1', 'product-1', { scene: 'PRODUCT_LIBRARY' }, config)

    expect(request.post).toHaveBeenCalledWith(
      '/colonel/activities/activity-1/products/product-1/promotion-links',
      { scene: 'PRODUCT_LIBRARY' },
      config
    )
  })
})
