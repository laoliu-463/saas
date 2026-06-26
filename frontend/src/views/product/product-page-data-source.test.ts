import { describe, expect, it } from 'vitest'

import {
  buildActivityProductListRoute,
  isProductManageProductsPath,
  normalizeActivityQueryId,
  PRODUCT_MANAGE_PRODUCTS_PATH,
  resolveActivityContextForManageProductsPath,
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

  it('builds activity product list route with query activity id', () => {
    expect(normalizeActivityQueryId(['3916506'])).toBe('3916506')
    expect(buildActivityProductListRoute('3916506')).toEqual({
      path: PRODUCT_MANAGE_PRODUCTS_PATH,
      query: { activityId: '3916506' }
    })
  })

  it('uses first assigned activity for /product/manage/products without query', () => {
    expect(resolveActivityContextForManageProductsPath(
      { path: PRODUCT_MANAGE_PRODUCTS_PATH, query: {} },
      [{ label: '星链达客-zy (3916506)', value: '3916506' }]
    )).toEqual({
      status: 'ready',
      activityId: '3916506',
      activityName: '星链达客-zy'
    })
  })

  it('resolves empty state for /product/manage/products without query or assigned activities', () => {
    expect(resolveActivityContextForManageProductsPath(
      { path: PRODUCT_MANAGE_PRODUCTS_PATH, query: {} },
      []
    )).toEqual({ status: 'empty' })
  })

  it('resolves ready state for assigned activity query', () => {
    expect(resolveActivityContextForManageProductsPath(
      { path: PRODUCT_MANAGE_PRODUCTS_PATH, query: { activityId: '3916506' } },
      [{ label: '星链达客-zy (3916506)', value: '3916506' }]
    )).toEqual({
      status: 'ready',
      activityId: '3916506',
      activityName: '星链达客-zy'
    })
  })

  it('resolves forbidden state for non-assigned activity query', () => {
    expect(resolveActivityContextForManageProductsPath(
      { path: PRODUCT_MANAGE_PRODUCTS_PATH, query: { activityId: '99999999' } },
      [{ label: '星链达客-zy (3916506)', value: '3916506' }]
    )).toEqual({
      status: 'forbidden',
      activityId: '99999999'
    })
  })

  it('resolves loading state while assigned activity options are loading', () => {
    expect(resolveActivityContextForManageProductsPath(
      { path: PRODUCT_MANAGE_PRODUCTS_PATH, query: { activityId: '3916506' } },
      [],
      { loading: true }
    )).toEqual({
      status: 'loading',
      activityId: '3916506'
    })
  })
})
