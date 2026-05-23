import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { pinActivityProduct, unpinActivityProduct } from './activityProduct'

vi.mock('../utils/request', () => ({
  default: {
    post: vi.fn(),
    delete: vi.fn()
  }
}))

describe('activity product API', () => {
  it('calls the backend pin endpoint', () => {
    pinActivityProduct('ACT-1', 'PROD-1')

    expect(request.post).toHaveBeenCalledWith('/colonel/activities/ACT-1/products/PROD-1/pin')
  })

  it('calls the backend unpin endpoint', () => {
    unpinActivityProduct('ACT-1', 'PROD-1')

    expect(request.delete).toHaveBeenCalledWith('/colonel/activities/ACT-1/products/PROD-1/pin')
  })
})
