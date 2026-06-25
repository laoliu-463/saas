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
})

describe('resolveActivityContextForManageProductsPath [PRODUCT-FIX-001]', () => {
  const baseArgs = {
    routePath: PRODUCT_MANAGE_PRODUCTS_PATH,
    assignedOptions: [
      { label: '春季大促 (3916506)', value: '3916506' },
      { label: '夏季新品 (3916507)', value: '3916507' }
    ],
    isAdmin: false,
    isOptionsLoading: false
  }

  it('returns empty status when no query (NO FALLBACK to assigned[0])', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      queryActivityId: ''
    })
    expect(ctx).toEqual({ status: 'empty' })
  })

  it('returns ready status when query matches assigned option', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      queryActivityId: '3916506'
    })
    expect(ctx.status).toBe('ready')
    expect(ctx.activityId).toBe('3916506')
    expect(ctx.activityName).toBe('春季大促 (3916506)')
  })

  it('returns forbidden status when query NOT in assigned list', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      queryActivityId: '99999999'
    })
    expect(ctx.status).toBe('forbidden')
    expect(ctx.activityId).toBe('99999999')
  })

  it('returns loading status when options still loading with valid query', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      queryActivityId: '3916506',
      isOptionsLoading: true
    })
    expect(ctx.status).toBe('loading')
    expect(ctx.activityId).toBe('3916506')
  })

  it('admin bypasses assigned check', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      queryActivityId: '99999999',
      isAdmin: true
    })
    expect(ctx.status).toBe('ready')
    expect(ctx.activityId).toBe('99999999')
  })

  it('returns empty for paths other than /product/manage/products', () => {
    const ctx = resolveActivityContextForManageProductsPath({
      ...baseArgs,
      routePath: '/product',
      queryActivityId: '3916506'
    })
    expect(ctx).toEqual({ status: 'empty' })
  })
})