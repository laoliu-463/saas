import { describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { getPartnerDetail, getPartnerProducts, listPartners } from './product'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

describe('product domain API', () => {
  it('calls the product-domain partner list endpoint', () => {
    listPartners({ keyword: '清风', type: 'MERCHANT', page: 2, size: 5 })

    expect(request.get).toHaveBeenCalledWith('/colonel/partners', {
      params: { keyword: '清风', type: 'MERCHANT', page: 2, size: 5 }
    })
  })

  it('calls the product-domain partner detail endpoint', () => {
    getPartnerDetail('1001', { type: 'MERCHANT' })

    expect(request.get).toHaveBeenCalledWith('/colonel/partners/1001', {
      params: { type: 'MERCHANT' }
    })
  })

  it('calls the product-domain partner products endpoint', () => {
    getPartnerProducts('1001', { page: 1, size: 10 })

    expect(request.get).toHaveBeenCalledWith('/colonel/partners/1001/products', {
      params: { page: 1, size: 10 }
    })
  })
})
