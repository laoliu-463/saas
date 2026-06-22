import { describe, expect, it } from 'vitest'

import {
  isProductManageProductsPath,
  PRODUCT_MANAGE_PRODUCTS_PATH,
  shouldLoadActivityProducts
} from './product-page-data-source'

describe('product-page-data-source', () => {
  it('treats /product/manage/products as activity product source', () => {
    expect(isProductManageProductsPath(PRODUCT_MANAGE_PRODUCTS_PATH)).toBe(true)
    expect(shouldLoadActivityProducts(PRODUCT_MANAGE_PRODUCTS_PATH, false)).toBe(true)
  })

  it('keeps shared product library outside activity product source', () => {
    expect(shouldLoadActivityProducts('/product', false)).toBe(false)
  })

  it('uses activity product source for explicit activity routes', () => {
    expect(shouldLoadActivityProducts('/any/path', true)).toBe(true)
  })
})
